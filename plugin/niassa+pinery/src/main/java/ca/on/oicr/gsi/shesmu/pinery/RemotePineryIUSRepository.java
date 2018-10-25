package ca.on.oicr.gsi.shesmu.pinery;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.provenance.PineryProvenanceProvider;
import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.gsistd.input.Utils;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.RunDto;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices
public class RemotePineryIUSRepository implements PineryIUSRepository {
	private class PinerySource extends AutoUpdatingJsonFile<ObjectNode> {
		private List<PineryIUSValue> cache = Collections.emptyList();
		private Instant lastUpdated = Instant.EPOCH;
		private Map<String, String> properties = Collections.emptyMap();
		private Optional<PineryProvenanceProvider> provider = Optional.empty();

		public PinerySource(Path fileName) {
			super(fileName, ObjectNode.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("Pinery IUS Source: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					for (final Entry<String, String> property : properties.entrySet()) {
						renderer.line(property.getKey(), property.getValue());
					}
				}
			};
		}

		private Stream<PineryIUSValue> lanes(PineryProvenanceProvider provider, Map<String, Integer> badSetCounts,
				Map<String, String> runDirectories, Predicate<String> goodRun) {
			return Utils.stream(provider.getLaneProvenance())//
					.filter(lp -> goodRun.test(lp.getSequencerRunName()))//
					.filter(lp -> lp.getSkip() == null || !lp.getSkip())//
					.map(lp -> {
						final Set<String> badSetInRecord = new TreeSet<>();
						final String runDirectory = Utils.singleton(lp.getSequencerRunAttributes().get("run_dir"),
								reason -> badSetInRecord.add("run_dir:" + reason), true).orElse("");

						runDirectories.put(lp.getSequencerRunName(), runDirectory);
						final PineryIUSValue result = new PineryIUSValue(//
								runDirectory, //
								"", //
								"", //
								"", //
								new Tuple(lp.getSequencerRunName(), Utils.parseLaneNumber(lp.getLaneNumber()),
										"NoIndex"), //
								"", //
								"", //
								"", //
								"", //
								"", //
								"", //
								"", //
								"", //
								0L, //
								"", //
								"", //
								lp.getLastModified() == null ? Instant.EPOCH : lp.getLastModified().toInstant(), //
								new Tuple(lp.getLaneProvenanceId(), lp.getVersion(),
										properties.getOrDefault("provider", "unknown")), //
								lp.getCreatedDate() == null ? Instant.EPOCH : lp.getCreatedDate().toInstant(), //
								false);

						if (badSetInRecord.isEmpty()) {
							return result;
						} else {
							badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
							return null;
						}
					})//
					.filter(Objects::nonNull);
		}

		private Stream<PineryIUSValue> samples(PineryProvenanceProvider provider, Map<String, Integer> badSetCounts,
				Map<String, String> runDirectories, Predicate<String> goodRun) {
			return Utils.stream(provider.getSampleProvenance())//
					.filter(sp -> goodRun.test(sp.getSequencerRunName()))//
					.map(sp -> {
						final String runDirectory = runDirectories.get(sp.getSequencerRunName());
						if (runDirectory == null) {
							return null;
						}
						final Set<String> badSetInRecord = new TreeSet<>();
						final PineryIUSValue result = new PineryIUSValue(//
								runDirectory, //
								sp.getStudyTitle(), //
								sp.getSampleName(), //
								sp.getRootSampleName(), //
								new Tuple(sp.getSequencerRunName(), Utils.parseLaneNumber(sp.getLaneNumber()),
										sp.getIusTag()), //
								limsAttr(sp, "geo_library_source_template_type", badSetInRecord::add, true).orElse(""), //
								limsAttr(sp, "geo_tissue_type", badSetInRecord::add, true).orElse(""), //
								limsAttr(sp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""), //
								limsAttr(sp, "geo_tissue_preparation", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_targeted_resequencing", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_tissue_region", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_group_id", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_group_id_description", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_library_size_code", badSetInRecord::add, false).map(Utils::parseLong)
										.orElse(0L), //
								limsAttr(sp, "geo_library_type", badSetInRecord::add, false).orElse(""), //
								limsAttr(sp, "geo_prep_kit", badSetInRecord::add, false).orElse(""), //
								sp.getLastModified() == null ? Instant.EPOCH : sp.getLastModified().toInstant(), //
								new Tuple(sp.getSampleProvenanceId(), sp.getVersion(),
										properties.getOrDefault("provider", "unknown")), //
								sp.getCreatedDate() == null ? Instant.EPOCH : sp.getCreatedDate().toInstant(), //
								true);

						if (badSetInRecord.isEmpty()) {
							return result;
						} else {
							badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
							return null;
						}
					})//
					.filter(Objects::nonNull);

		}

		public Stream<PineryIUSValue> stream() {
			if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
				provider.ifPresent(provider -> {
					try (AutoCloseable timer = fetchLatency.start()) {
						final Map<String, Integer> badSetCounts = new TreeMap<>();
						final Map<String, String> runDirectories = new HashMap<>();
						final Set<String> completeRuns;
						try (PineryClient client = new PineryClient(properties.get("url"), true)) {
							completeRuns = client.getSequencerRun().all().stream()//
									.filter(run -> run.getState().equals("Completed"))//
									.map(RunDto::getName)//
									.collect(Collectors.toSet());
						}
						cache = Stream.concat(lanes(provider, badSetCounts, runDirectories, completeRuns::contains), //
								samples(provider, badSetCounts, runDirectories, completeRuns::contains))//
								.collect(Collectors.toList());
						count.labels(fileName().toString()).set(cache.size());
						lastUpdated = Instant.now();
						lastFetchTime.labels(fileName().toString()).setToCurrentTime();
						badSetCounts.entrySet()
								.forEach(e -> badSetMap
										.labels(Stream.concat(Stream.of(fileName().toString()),
												Stream.of(e.getKey().split(":"))).toArray(String[]::new))
										.set(e.getValue()));
					} catch (final Exception e) {
						e.printStackTrace();
						provenanceError.labels(fileName().toString()).inc();
					}
				});
			}
			return cache.stream();
		}

		@Override
		public Optional<Integer> update(ObjectNode value) {
			properties = RuntimeSupport.stream(value.fields())
					.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().asText()));
			properties.put("file", fileName().toString());
			provider = Optional.of(new PineryProvenanceProvider(properties));
			return Optional.empty();
		}
	}

	private static final Gauge badSetMap = Gauge
			.build("shesmu_pinery_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("target", "property", "reason").register();

	private static final Gauge count = Gauge
			.build("shesmu_pinery_last_count", "The number of lanes and samples from Pinery.").labelNames("target")
			.register();

	public static final String EXTENSION = ".pinery";

	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_pinery_lane_request_time",
			"The time to fetch data from Provenance.");

	private static final Gauge lastFetchTime = Gauge
			.build("shesmu_pinery_last_fetch_time",
					"The time, in seconds since the epoch, when the last fetch from Pinery occured.")
			.labelNames("target").register();

	private static final Counter provenanceError = Counter
			.build("shesmu_pinery_error", "The number of times calling out to Pinery has failed.").labelNames("target")
			.register();

	public static Optional<String> limsAttr(SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
		return Utils.singleton(sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
	}

	private final AutoUpdatingDirectory<PinerySource> sources;

	public RemotePineryIUSRepository() {
		sources = new AutoUpdatingDirectory<>(EXTENSION, PinerySource::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return sources.stream().map(PinerySource::configuration);
	}

	@Override
	public Stream<PineryIUSValue> stream() {
		return sources.stream().flatMap(PinerySource::stream);
	}

}
