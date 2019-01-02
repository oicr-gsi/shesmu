package ca.on.oicr.gsi.shesmu.gsistd.input;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.ProviderLoader;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;

@MetaInfServices
public class FileProvenanceGsiStdRepository implements GsiStdRepository {
	private class PipeDevConfiguration implements FileBackedConfiguration {
		private class ItemCache extends ValueCache<Stream<GsiStdValue>> {

			public ItemCache() {
				super("pipedev " + fileName.toString(), 60, ReplacingRecord::new);
			}

			@Override
			protected Stream<GsiStdValue> fetch(Instant lastUpdated) throws Exception {
				final AtomicInteger badSets = new AtomicInteger();
				final AtomicInteger badVersions = new AtomicInteger();
				final Map<String, Integer> badSetCounts = new TreeMap<>();
				return client.map(c -> Utils.stream(c.getFileProvenance(PROVENANCE_FILTER))).orElseGet(Stream::empty)//
						.filter(fp -> fp.getSkip() == null || fp.getSkip().equals("false"))//
						.map(fp -> {
							final AtomicReference<Boolean> badRecord = new AtomicReference<>(false);
							final Set<String> badSetInRecord = new TreeSet<>();
							final Optional<LimsKey> limsKey = Utils.singleton(fp.getIusLimsKeys(),
									reason -> badSetInRecord.add("limskey:" + reason), true)
									.map(IusLimsKey::getLimsKey);
							final GsiStdValue result = new GsiStdValue(//
									fp.getFileSWID().toString(), //
									Paths.get(fp.getFilePath()), //
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
									fp.getLastModified().toInstant(), //
									"file_provenance");

							if (!badSetInRecord.isEmpty()) {
								badSets.incrementAndGet();
								badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
								return null;
							}
							return badRecord.get() ? null : result;
						})//
						.filter(Objects::nonNull)//
						.onClose(() -> {
							badSetError.labels(fileName.toString()).set(badSets.get());
							badWorkflowVersions.labels(fileName.toString()).set(badVersions.get());
							badSetCounts.entrySet()
									.forEach(e -> badSetMap
											.labels(Stream.concat(Stream.of(fileName.toString()),
													COLON.splitAsStream(e.getKey())).toArray(String[]::new))
											.set(e.getValue()));
						});
			}

		}

		private final ItemCache cache;

		private Optional<DefaultProvenanceClient> client = Optional.empty();

		private final Path fileName;

		private boolean ok;

		public PipeDevConfiguration(Path fileName) {
			super();
			this.fileName = fileName;
			cache = new ItemCache();
		}

		@Override
		public ConfigurationSection configuration() {
			return new ConfigurationSection(fileName().toString()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Configuration Good?", ok ? "Yes" : "No");
				}
			};
		}

		@Override
		public Path fileName() {
			return fileName;
		}

		@Override
		public void start() {
			update();
		}

		@Override
		public void stop() {
			// Do nothing.

		}

		public Stream<GsiStdValue> stream() {
			return cache.get();
		}

		@Override
		public Optional<Integer> update() {
			try {
				final DefaultProvenanceClient client = new DefaultProvenanceClient();
				final ProviderLoader loader = new ProviderLoader(new String(Files.readAllBytes(fileName())));
				setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
				setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
				setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
				this.client = Optional.of(client);
				cache.invalidate();
				ok = true;
			} catch (final Exception e) {
				e.printStackTrace();
				ok = false;
			}
			return Optional.empty();
		}
	}

	private static final Gauge badSetError = Gauge
			.build("shesmu_file_provenance_bad_set_size",
					"The number of records where a set contained not exactly one item.")
			.labelNames("filename").register();

	private static final Gauge badSetMap = Gauge
			.build("shesmu_file_provenance_bad_set",
					"The number of provenace records with sets not containing exactly one item.")
			.labelNames("filename", "property", "reason").register();

	private static final Gauge badWorkflowVersions = Gauge
			.build("shesmu_file_provenance_bad_workflow",
					"The number of records with a bad workflow version (not x.y.z) was received from Provenance.")
			.labelNames("filename").register();

	private static final Pattern COLON = Pattern.compile(":");

	private static final Map<FileProvenanceFilter, Set<String>> PROVENANCE_FILTER = new EnumMap<>(
			FileProvenanceFilter.class);

	private static final Pattern WORKFLOW_VERSION2 = Pattern.compile("^(\\d+)\\.(\\d+)$");

	private static final Pattern WORKFLOW_VERSION3 = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

	static {
		PROVENANCE_FILTER.put(FileProvenanceFilter.processing_status, Collections.singleton("success"));
		PROVENANCE_FILTER.put(FileProvenanceFilter.workflow_run_status, Collections.singleton("completed"));
		PROVENANCE_FILTER.put(FileProvenanceFilter.skip, Collections.singleton("false"));
	}

	private static Optional<String> limsAttr(FileProvenance fp, String key, Consumer<String> isBad, boolean required) {
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

	public static <T> void setProvider(Map<String, T> source, BiConsumer<String, T> consumer) {
		source.entrySet().stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
	}

	private final AutoUpdatingDirectory<PipeDevConfiguration> configurations = new AutoUpdatingDirectory<>(".pipedev",
			PipeDevConfiguration::new);

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(PipeDevConfiguration::configuration);
	}

	@Override
	public Stream<GsiStdValue> stream() {
		return configurations.stream().flatMap(PipeDevConfiguration::stream);
	}
}
