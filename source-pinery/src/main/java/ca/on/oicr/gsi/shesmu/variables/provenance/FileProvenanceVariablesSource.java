package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.FileProvenance.Status;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.Variables;
import ca.on.oicr.gsi.shesmu.VariablesSource;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices
public class FileProvenanceVariablesSource implements VariablesSource {

	private static final Gauge badSetError = Gauge.build("shesmu_file_provenance_bad_set_size",
			"The number of records where a set contained not exactly one item.").register();

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

	private static final Counter provenanceError = Counter
			.build("shesmu_file_provenance_error", "The number of times calling out to Provenance has failed.")
			.register();

	private static final Pattern WORKFLOW_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

	public static Optional<String> limsAttr(FileProvenance fp, String key, Runnable isBad) {
		return Utils.singleton(fp.getSampleAttributes().get(key), isBad);
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
		final Matcher m = WORKFLOW_VERSION.matcher(input);
		if (m.matches()) {
			return new Tuple(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)));
		}
		isBad.run();
		return new Tuple(0L, 0L, 0L);
	}

	private static <T> void setProvider(Map<String, T> source, BiConsumer<String, T> consumer) {
		source.entrySet().stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
	}

	private List<Variables> cache = Collections.emptyList();

	private final DefaultProvenanceClient client = new DefaultProvenanceClient();
	private Instant lastUpdated = Instant.EPOCH;

	private final Map<String, String> properties = new TreeMap<>();

	public FileProvenanceVariablesSource() throws IOException {
		Utils.LOADER.ifPresent(loader -> {
			setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
			setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
			setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
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
	public Stream<Variables> stream() {
		if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
			try (AutoCloseable timer = fetchLatency.start()) {
				final AtomicInteger badSets = new AtomicInteger();
				final AtomicInteger badVersions = new AtomicInteger();
				cache = client.getFileProvenance().stream()//
						.filter(fp -> fp.getStatus() == Status.OKAY && !fp.getSkip().equals("true"))//
						.map(fp -> {
							final AtomicReference<Boolean> badRecord = new AtomicReference<>(false);
							final AtomicReference<Boolean> badSetInRecord = new AtomicReference<>(false);
							final Runnable badAttr = () -> {
								badRecord.set(true);
								badSetInRecord.set(true);
							};
							final Variables result = new Variables(//
									fp.getFileSWID().toString(), //
									fp.getFilePath(), //
									fp.getFileMetaType(), //
									fp.getFileMd5sum(), //
									Utils.parseLong(fp.getFileSize()), //
									fp.getWorkflowName(), parseWorkflowVersion(fp.getWorkflowVersion(), () -> {
										badVersions.incrementAndGet();
										badRecord.set(true);
									}), //
									Utils.singleton(fp.getStudyTitles(), badAttr).orElse(""), //
									Utils.singleton(fp.getSampleNames(), badAttr).orElse(""), //
									Utils.singleton(fp.getRootSampleNames(), badAttr).orElse(""), //
									packIUS(fp), //
									limsAttr(fp, "geo_library_source_template_type", badAttr).orElse(""), //
									limsAttr(fp, "geo_tissue_type", badAttr).orElse(""), //
									limsAttr(fp, "geo_tissue_origin", badAttr).orElse(""), //
									limsAttr(fp, "geo_tissue_preparation", badAttr).orElse(""), //
									limsAttr(fp, "geo_targeted_resequencing", badAttr).orElse(""), //
									limsAttr(fp, "geo_tissue_region", badAttr).orElse(""), //
									limsAttr(fp, "geo_group_id", badAttr).orElse(""), //
									limsAttr(fp, "geo_group_id_description", badAttr).orElse(""), //
									limsAttr(fp, "geo_library_size_code", badAttr).map(Utils::parseLong).orElse(0L), //
									limsAttr(fp, "geo_library_type", badAttr).orElse(""), //
									fp.getLastModified().toInstant(), //
									"file_provenance");

							if (badSetInRecord.get()) {
								badSets.incrementAndGet();
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
			} catch (final Exception e) {
				e.printStackTrace();
				provenanceError.inc();
			}
		}
		return cache.stream();
	}

}
