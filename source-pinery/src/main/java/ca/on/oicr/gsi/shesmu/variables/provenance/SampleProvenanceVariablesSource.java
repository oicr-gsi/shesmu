package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.provenance.SampleProvenanceProvider;
import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.Variables;
import ca.on.oicr.gsi.shesmu.VariablesSource;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices(VariablesSource.class)
public class SampleProvenanceVariablesSource implements VariablesSource {
	private static final Gauge badSetMap = Gauge
			.build("shesmu_sample_provenance_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("property", "reason").register();
	private static final Gauge count = Gauge
			.build("shesmu_sample_provenance_last_count", "The number of items from Provenance occured.").register();
	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_sample_provenance_request_time",
			"The time to fetch data from Provenance.");

	private static final Gauge lastFetchTime = Gauge.build("shesmu_sample_provenance_last_fetch_time",
			"The time, in seconds since the epoch, when the last fetch from Provenance occured.").register();

	private static final Counter provenanceError = Counter
			.build("shesmu_sample_provenance_error", "The number of times calling out to Provenance has failed.")
			.register();

	private static final Tuple VERSION = new Tuple(0L, 0L, 0L);

	public static Optional<String> limsAttr(SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
		return Utils.singleton(sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
	}

	private List<Variables> cache = Collections.emptyList();

	private Instant lastUpdated = Instant.EPOCH;

	final List<SampleProvenanceProvider> provider = Utils.LOADER
			.<List<SampleProvenanceProvider>>map(p -> new ArrayList<>(p.getSampleProvenanceProviders().values()))
			.orElseGet(Collections::emptyList);

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		final Map<String, String> properties = new TreeMap<>();
		properties.put("sample provider", Integer.toString(provider.size()));
		return Stream.of(new Pair<>("Sample Provenance Variable Source", properties));
	}

	@Override
	public Stream<Variables> stream() {
		if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
			try (AutoCloseable timer = fetchLatency.start()) {
				final Map<String, Integer> badSetCounts = new TreeMap<>();
				cache = provider.stream()//
						.flatMap(provider -> Utils.stream(provider.getSampleProvenance()))//
						.map(sp -> {
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
									new Tuple(sp.getSequencerRunName(), Utils.parseLaneNumber(sp.getLaneNumber()),
											sp.getIusTag()), //
									limsAttr(sp, "geo_library_source_template_type", badSetInRecord::add, true)
											.orElse(""), //
									limsAttr(sp, "geo_tissue_type", badSetInRecord::add, true).orElse(""), //
									limsAttr(sp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""), //
									limsAttr(sp, "geo_tissue_preparation", badSetInRecord::add, false).orElse(""), //
									limsAttr(sp, "geo_targeted_resequencing", badSetInRecord::add, false).orElse(""), //
									limsAttr(sp, "geo_tissue_region", badSetInRecord::add, false).orElse(""), //
									limsAttr(sp, "geo_group_id", badSetInRecord::add, false).orElse(""), //
									limsAttr(sp, "geo_group_id_description", badSetInRecord::add, false).orElse(""), //
									limsAttr(sp, "geo_library_size_code", badSetInRecord::add, false)
											.map(Utils::parseLong).orElse(0L), //
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
						.filter(Objects::nonNull)//
						.collect(Collectors.toList());
				count.set(cache.size());
				lastUpdated = Instant.now();
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
