package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.ProviderLoader;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.FileProvenance.Status;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices
public class ProvenanceVariablesSource implements VariablesSource {

	private static final Counter badWorkflowVersions = Counter.build("shesmu_provenance_bad_workflow",
			"The number of times a bad workflow version (not x.y.z) was received from Provenance.").register();
	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_provenance_request_time",
			"The time to fetch data from Provenance.");
	private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");

	private static final Gauge lastFetchTime = Gauge.build("shesmu_provenance_last_fetch_time",
			"The time, in seconds since the epoch, when the last fetch from Provenance occured.").register();
	private static final Counter provenanceError = Counter
			.build("shesmu_provenance_error", "The number of times calling out to Provenance has failed.").register();
	private static final Pattern WORKFLOW_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

	private static Set<String> limsAttr(FileProvenance fp, String key) {
		return fp.getSampleAttributes().get(key).stream().collect(Collectors.toSet());
	}

	private static Tuple packIUS(FileProvenance fp) {
		Iterator<String> runName = fp.getSequencerRunNames().iterator();
		Iterator<String> laneName = fp.getLaneNames().iterator();
		Iterator<String> tagName = fp.getIusTags().iterator();
		if (runName.hasNext() && laneName.hasNext() && tagName.hasNext()) {
			Matcher laneMatcher = LANE_NUMBER.matcher(laneName.next());
			if (laneMatcher.matches()) {
				return new Tuple(runName.next(), parseLong(laneMatcher.group(1)), tagName.next());
			}
		}
		return new Tuple("", 0L, "");
	}

	private static long parseLong(String input) {
		try {
			return Long.parseLong(input);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Tuple parseWorkflowVersion(String input) {
		Matcher m = WORKFLOW_VERSION.matcher(input);
		if (m.matches()) {
			return new Tuple(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)));
		}
		badWorkflowVersions.inc();
		return new Tuple(0L, 0L, 0L);
	}

	private static <T> void setProvider(Map<String, T> source, BiConsumer<String, T> consumer) {
		source.entrySet().stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
	}

	private List<Variables> cache = Collections.emptyList();

	private final DefaultProvenanceClient client = new DefaultProvenanceClient();

	private Instant lastUpdated = Instant.EPOCH;

	public ProvenanceVariablesSource() throws IOException {
		Optional.ofNullable(System.getenv("PROVENANCE_SETTINGS"))//
				.map(Paths::get)//
				.flatMap(path -> {
					try {
						return Optional.of(new ProviderLoader(new String(Files.readAllBytes(path))));

					} catch (Exception e) {
						e.printStackTrace();
						return Optional.empty();
					}
				})//
				.ifPresent(loader -> {

					setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
					setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
					setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
				});
	}

	@Override
	public Stream<Variables> stream() {
		if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.MINUTES) > 15) {
			try (AutoCloseable timer = fetchLatency.start()) {
				cache = client.getFileProvenance().stream()//
						.filter(fp -> fp.getStatus() == Status.OKAY && !fp.getSkip().equals("true"))//
						.map(fp -> new Variables(//
								fp.getFileSWID().longValue(), //
								fp.getFilePath(), //
								fp.getFileMetaType(), //
								fp.getFileMd5sum(), //
								parseLong(fp.getFileSize()), //
								fp.getWorkflowName(), parseWorkflowVersion(fp.getWorkflowVersion()), //
								new HashSet<>(fp.getStudyTitles()), //
								fp.getSampleNames().stream().collect(Collectors.toSet()), //
								fp.getRootSampleNames().stream().collect(Collectors.toSet()), //
								packIUS(fp), //
								limsAttr(fp, "geo_library_source_template_type"), //
								limsAttr(fp, "geo_tissue_type"), //
								limsAttr(fp, "geo_tissue_origin"), //
								limsAttr(fp, "geo_tissue_preparation"), //
								limsAttr(fp, "geo_targeted_resequencing"), //
								limsAttr(fp, "geo_tissue_region"), //
								limsAttr(fp, "geo_group_id"), //
								limsAttr(fp, "geo_group_id_description"), //
								fp.getSampleAttributes().get("geo_library_size_code").stream()
										.map(ProvenanceVariablesSource::parseLong).collect(Collectors.toSet()), //
								limsAttr(fp, "geo_library_type").stream().collect(Collectors.toSet())))
						.collect(Collectors.toList());
				lastUpdated = Instant.now();
				lastFetchTime.setToCurrentTime();
			} catch (Exception e) {
				e.printStackTrace();
				provenanceError.inc();
			}
		}
		return cache.stream();
	}

}
