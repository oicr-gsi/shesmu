package ca.on.oicr.gsi.shesmu.pinery;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.gsistd.input.Utils;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.HttpResponseException;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.RunDto;
import io.prometheus.client.Gauge;

@MetaInfServices
public class RemotePineryIUSRepository implements PineryIUSRepository {

	private class PinerySource extends AutoUpdatingJsonFile<PineryConfiguration> {
		private final class ItemCache extends ValueCache<Stream<PineryIUSValue>> {
			private ItemCache(Path fileName) {
				super("pinery " + fileName.toString(), 30, ReplacingRecord::new);
			}

			@Override
			protected Stream<PineryIUSValue> fetch(Instant lastUpdated) throws Exception {
				if (!config.isPresent()) {
					return Stream.empty();
				}
				final PineryConfiguration cfg = config.get();
				try (PineryClient c = new PineryClient(cfg.getUrl(), true)) {
					final Map<String, Integer> badSetCounts = new TreeMap<>();
					final Map<String, String> runDirectories = new HashMap<>();
					final Set<String> completeRuns = c.getSequencerRun().all().stream()//
							.filter(run -> run.getState().equals("Completed"))//
							.map(RunDto::getName)//
							.collect(Collectors.toSet());
					return Stream.concat(//
							lanes(c, cfg.getProvider(), badSetCounts, runDirectories, completeRuns::contains), //
							samples(c, cfg.getProvider(), badSetCounts, runDirectories, completeRuns::contains))//
							.onClose(() -> badSetCounts.entrySet()
									.forEach(e -> badSetMap
											.labels(Stream.concat(Stream.of(fileName().toString()),
													Stream.of(e.getKey().split(":"))).toArray(String[]::new))
											.set(e.getValue())));
				}
			}

			private Stream<PineryIUSValue> lanes(PineryClient client, String provider,
					Map<String, Integer> badSetCounts, Map<String, String> runDirectories, Predicate<String> goodRun)
					throws HttpResponseException {
				return Utils.stream(client.getLaneProvenance().all())//
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
									new Tuple(lp.getLaneProvenanceId(), lp.getVersion(), provider), //
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

			private Stream<PineryIUSValue> samples(PineryClient client, String provider,
					Map<String, Integer> badSetCounts, Map<String, String> runDirectories, Predicate<String> goodRun)
					throws HttpResponseException {
				return Utils.stream(client.getSampleProvenance().all())//
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
									limsAttr(sp, "geo_prep_kit", badSetInRecord::add, false).orElse(""), //
									sp.getLastModified() == null ? Instant.EPOCH : sp.getLastModified().toInstant(), //
									new Tuple(sp.getSampleProvenanceId(), sp.getVersion(), provider), //
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
		}

		private final ItemCache cache;
		private Optional<PineryConfiguration> config = Optional.empty();

		public PinerySource(Path fileName) {
			super(fileName, PineryConfiguration.class);
			cache = new ItemCache(fileName);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection(fileName().toString()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					final Optional<String> url = config.map(PineryConfiguration::getUrl);
					renderer.link("URL", url.orElse("about:blank"), url.orElse("Unknown"));
					renderer.line("Provider", config.map(PineryConfiguration::getProvider).orElse("Unknown"));
				}
			};
		}

		public Stream<PineryIUSValue> stream() {
			return cache.get();
		}

		@Override
		public Optional<Integer> update(PineryConfiguration value) {
			config = Optional.of(value);
			cache.invalidate();
			return Optional.empty();
		}
	}

	private static final Gauge badSetMap = Gauge
			.build("shesmu_pinery_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("target", "property", "reason").register();

	static final String EXTENSION = ".pinery";

	private static Optional<String> limsAttr(SampleProvenance sp, String key, Consumer<String> isBad,
			boolean required) {
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
