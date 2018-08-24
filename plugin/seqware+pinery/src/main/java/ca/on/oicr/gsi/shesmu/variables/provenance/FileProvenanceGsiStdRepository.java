package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.GsiStdRepository;
import ca.on.oicr.gsi.shesmu.GsiStdValue;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.Tuple;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices
public class FileProvenanceGsiStdRepository implements GsiStdRepository {

	private static final Gauge badSetError = Gauge.build("shesmu_file_provenance_bad_set_size",
			"The number of records where a set contained not exactly one item.").register();

	private static final Gauge badSetMap = Gauge
			.build("shesmu_file_provenance_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("property", "reason").register();

	private static final Gauge badWorkflowVersions = Gauge
			.build("shesmu_file_provenance_bad_workflow",
					"The number of records with a bad workflow version (not x.y.z) was received from Provenance.")
			.register();

	private static final Gauge count = Gauge
			.build("shesmu_file_provenance_last_count", "The number of items from Provenance occured.").register();

	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_file_provenance_request_time",
			"The time to fetch data from Provenance.");

	private static final Gauge lastFetchTime = Gauge.build("shesmu_file_provenance_last_fetch_time",
			"The time, in seconds since the epoch, when the last fetch from Provenance occured.").register();

	private static final Map<FileProvenanceFilter, Set<String>> PROVENANCE_FILTER = new EnumMap<>(
			FileProvenanceFilter.class);

	private static final Counter provenanceError = Counter
			.build("shesmu_file_provenance_error", "The number of times calling out to Provenance has failed.")
			.register();

	private static final Pattern WORKFLOW_VERSION2 = Pattern.compile("^(\\d+)\\.(\\d+)$");
	private static final Pattern WORKFLOW_VERSION3 = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

	static {
		PROVENANCE_FILTER.put(FileProvenanceFilter.processing_status, Collections.singleton("success"));
		PROVENANCE_FILTER.put(FileProvenanceFilter.workflow_run_status, Collections.singleton("completed"));
		PROVENANCE_FILTER.put(FileProvenanceFilter.skip, Collections.singleton("false"));
	}

	public static Optional<String> limsAttr(FileProvenance fp, String key, Consumer<String> isBad, boolean required) {
		return Utils.singleton(fp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
	}

	private static Tuple packIUS(FileProvenance fp) {
		final Iterator<String> runName = fp.getSequencerRunNames().iterator();
		final Iterator<String> laneName = fp.getLaneNames().iterator();
		final Iterator<String> tagName = fp.getIusTags().iterator();
		if (runName.hasNext() && laneName.hasNext() && tagName.hasNext()) {
			return new Tuple(runName.next(), Utils.parseLaneNumber(laneName.next()), tagName.next());
		}
		return new Tuple("", 0L, "");
	}

	private static Tuple parseWorkflowVersion(String input, Runnable isBad) {
		final Matcher m3 = WORKFLOW_VERSION3.matcher(input);
		if (m3.matches()) {
			return new Tuple(Long.parseLong(m3.group(1)), Long.parseLong(m3.group(2)), Long.parseLong(m3.group(3)));
		}
		final Matcher m2 = WORKFLOW_VERSION2.matcher(input);
		if (m2.matches()) {
			return new Tuple(Long.parseLong(m2.group(1)), Long.parseLong(m2.group(2)), 0L);
		}
		isBad.run();
		return new Tuple(0L, 0L, 0L);
	}

	private List<GsiStdValue> cache = Collections.emptyList();

	private final DefaultProvenanceClient client = new DefaultProvenanceClient();

	private Instant lastUpdated = Instant.EPOCH;

	private final Map<String, String> properties = new TreeMap<>();

	public FileProvenanceGsiStdRepository() throws IOException {
		Utils.LOADER.ifPresent(loader -> {
			Utils.setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
			Utils.setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
			Utils.setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
			properties.put("analyis providers", Integer.toString(loader.getAnalysisProvenanceProviders().size()));
			properties.put("lane providers", Integer.toString(loader.getLaneProvenanceProviders().size()));
			properties.put("sample providers", Integer.toString(loader.getSampleProvenanceProviders().size()));
		});
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.of(new Pair<>("File Provenance Variable Source", properties));
	}

	@Override
	public Stream<GsiStdValue> stream() {
		if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
			try (AutoCloseable timer = fetchLatency.start()) {
				final AtomicInteger badSets = new AtomicInteger();
				final AtomicInteger badVersions = new AtomicInteger();
				final Map<String, Integer> badSetCounts = new TreeMap<>();
				cache = Utils.stream(client.getFileProvenance(PROVENANCE_FILTER))//
						.filter(fp -> fp.getSkip() == null || fp.getSkip().equals("false"))//
						.map(fp -> {
							final AtomicReference<Boolean> badRecord = new AtomicReference<>(false);
							final Set<String> badSetInRecord = new TreeSet<>();
							final Optional<LimsKey> limsKey = Utils.singleton(fp.getIusLimsKeys(),
									reason -> badSetInRecord.add("limskey:" + reason), true)
									.map(IusLimsKey::getLimsKey);
							final GsiStdValue result = new GsiStdValue(//
									fp.getFileSWID().toString(), //
									fp.getFilePath(), //
									fp.getFileMetaType(), //
									fp.getFileMd5sum(), //
									Utils.parseLong(fp.getFileSize()), //
									fp.getWorkflowName(), //
									Optional.ofNullable(fp.getWorkflowRunSWID()).map(Object::toString).orElse(""), //
									parseWorkflowVersion(fp.getWorkflowVersion(), () -> {
										badVersions.incrementAndGet();
										badRecord.set(true);
									}), //
									Utils.singleton(fp.getStudyTitles(),
											reason -> badSetInRecord.add("study:" + reason), true).orElse(""), //
									Utils.singleton(fp.getSampleNames(),
											reason -> badSetInRecord.add("librarynames:" + reason), true).orElse(""), //
									Utils.singleton(fp.getRootSampleNames(),
											reason -> badSetInRecord.add("samplenames:" + reason), true).orElse(""), //
									packIUS(fp), //
									limsAttr(fp, "geo_library_source_template_type", badSetInRecord::add, true)
											.orElse(""), //
									limsAttr(fp, "geo_tissue_type", badSetInRecord::add, true).orElse(""), //
									limsAttr(fp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""), //
									limsAttr(fp, "geo_tissue_preparation", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_targeted_resequencing", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_tissue_region", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_group_id", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_group_id_description", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_library_size_code", badSetInRecord::add, false)
											.map(Utils::parseLong).orElse(0L), //
									limsAttr(fp, "geo_library_type", badSetInRecord::add, false).orElse(""), //
									limsAttr(fp, "geo_prep_kit", badSetInRecord::add, false).orElse(""), //
									fp.getLastModified().toInstant(), //
									new Tuple(limsKey.map(LimsKey::getId).orElse(""),
											limsKey.map(LimsKey::getVersion).orElse(""),
											limsKey.map(LimsKey::getProvider).orElse("")), //
									"file_provenance");

							if (!badSetInRecord.isEmpty()) {
								badSets.incrementAndGet();
								badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
								return null;
							}
							return badRecord.get() ? null : result;
						})//
						.filter(Objects::nonNull)//
						.collect(Collectors.toList());
				count.set(cache.size());
				lastUpdated = Instant.now();
				badSetError.set(badSets.get());
				badWorkflowVersions.set(badVersions.get());
				lastFetchTime.setToCurrentTime();
				badSetCounts.entrySet().forEach(e -> badSetMap.labels(e.getKey().split(":")).set(e.getValue()));
			} catch (final Exception e) {
				e.printStackTrace();
				provenanceError.inc();
			}
		}
		return cache.stream();
	}

}
