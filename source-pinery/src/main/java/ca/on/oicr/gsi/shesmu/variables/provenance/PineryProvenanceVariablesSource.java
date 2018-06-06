package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.provenance.PineryProvenanceProvider;
import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.Variables;
import ca.on.oicr.gsi.shesmu.VariablesSource;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices(VariablesSource.class)
public class PineryProvenanceVariablesSource implements VariablesSource {
	private class PinerySource extends AutoUpdatingJsonFile<ObjectNode> {
		private List<Variables> cache = Collections.emptyList();
		private Instant lastUpdated = Instant.EPOCH;
		private Map<String, String> properties = Collections.emptyMap();
		private Optional<PineryProvenanceProvider> provider = Optional.empty();

		public PinerySource(Path fileName) {
			super(fileName, ObjectNode.class);
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>("Sample/Lane Provenance Variable Source", properties);
		}

		private Stream<Variables> lanes(PineryProvenanceProvider provider, Map<String, Integer> badSetCounts) {
			return Utils.stream(provider.getLaneProvenance()).filter(lp -> lp.getSkip() == null || lp.getSkip())//
					.map(lp -> {
						final Set<String> badSetInRecord = new TreeSet<>();
						final Variables result = new Variables(//
								lp.getLaneProvenanceId(), //
								Utils.singleton(lp.getSequencerRunAttributes().get("run_dir"),
										reason -> badSetInRecord.add("run_dir:" + reason), true).orElse(""), //
								"inode/directory", //
								"0000000000000000000000000000000", //
								0, //
								"Sequencer", //
								lp.getSequencerRunName(), //
								VERSION, //
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
								lp.getCreatedDate() == null ? Instant.EPOCH : lp.getCreatedDate().toInstant(), //
								"lane_provenance");

						if (badSetInRecord.isEmpty()) {
							return result;
						} else {
							badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
							return null;
						}
					})//
					.filter(Objects::nonNull);
		}

		private Stream<Variables> samples(PineryProvenanceProvider provider, Map<String, Integer> badSetCounts) {
			return Utils.stream(provider.getSampleProvenance()).map(sp -> {
				final Set<String> badSetInRecord = new TreeSet<>();
				final Variables result = new Variables(//
						sp.getSampleProvenanceId(), //
						"", //
						"inode/directory", //
						"0000000000000000000000000000000", //
						0, //
						"Sequencer", //
						sp.getSequencerRunName(), //
						VERSION, //
						sp.getStudyTitle(), //
						sp.getSampleName(), //
						sp.getRootSampleName(), //
						new Tuple(sp.getSequencerRunName(), Utils.parseLaneNumber(sp.getLaneNumber()), sp.getIusTag()), //
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
						sp.getCreatedDate() == null ? Instant.EPOCH : sp.getCreatedDate().toInstant(), //
						"sample_provenance");

				if (badSetInRecord.isEmpty()) {
					return result;
				} else {
					badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
					return null;
				}
			})//
					.filter(Objects::nonNull);

		}

		public Stream<Variables> stream() {
			if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
				provider.ifPresent(provider -> {
					try (AutoCloseable timer = fetchLatency.start()) {
						final Map<String, Integer> badSetCounts = new TreeMap<>();

						cache = Stream.concat(lanes(provider, badSetCounts), samples(provider, badSetCounts))
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
			.build("shesmu_pinery_provenance_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("target", "property", "reason").register();

	private static final Gauge count = Gauge
			.build("shesmu_pinery_provenance_last_count", "The number of lanes and samples from Provenance.")
			.labelNames("target").register();

	private static final String EXTENSION = ".pinery";

	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_pinery_provenance_request_time",
			"The time to fetch data from Provenance.");

	private static final Gauge lastFetchTime = Gauge
			.build("shesmu_pinery_provenance_last_fetch_time",
					"The time, in seconds since the epoch, when the last fetch from Provenance occured.")
			.labelNames("target").register();

	private static final Counter provenanceError = Counter
			.build("shesmu_pinery_provenance_error", "The number of times calling out to Provenance has failed.")
			.labelNames("target").register();

	private static final Tuple VERSION = new Tuple(0L, 0L, 0L);

	public static Optional<String> limsAttr(SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
		return Utils.singleton(sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
	}

	private final AutoUpdatingDirectory<PinerySource> sources;

	public PineryProvenanceVariablesSource() {
		sources = new AutoUpdatingDirectory<>(EXTENSION, PinerySource::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return sources.stream().map(PinerySource::configuration);
	}

	@Override
	public Stream<Variables> stream() {
		return sources.stream().flatMap(PinerySource::stream);
	}

}
