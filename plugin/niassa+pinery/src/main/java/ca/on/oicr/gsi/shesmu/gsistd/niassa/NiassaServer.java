package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.util.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.TypeGlue;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.seqware.common.model.WorkflowRunStatus;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.metadata.MetadataWS;
import net.sourceforge.seqware.common.model.IUS;

class NiassaServer extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {
	private class AnalysisCache extends KeyValueCache<Long, Stream<AnalysisState>> {
		public AnalysisCache(Path fileName) {
			super("niassa-analysis " + fileName.toString(), 20, MergingRecord.by(AnalysisState::workflowRunAccession));
		}

		@Override
		protected Stream<AnalysisState> fetch(Long key, Instant lastUpdated) throws IOException {
			if (metadata == null) {
				return Stream.empty();
			}
			final Map<FileProvenanceFilter, Set<String>> filters = new EnumMap<>(FileProvenanceFilter.class);
			filters.put(FileProvenanceFilter.workflow, Collections.singleton(Long.toString(key)));
			return metadata.getAnalysisProvenance(filters).stream()//
					.filter(ap -> ap.getWorkflowId() != null && (ap.getSkip() == null || !ap.getSkip()))//
					.map(AnalysisState::new);
		}
	}

	private class SkipLaneCache extends ValueCache<Stream<Pair<Tuple, Tuple>>> {
		public SkipLaneCache(Path fileName) {
			super("niassa-skipped " + fileName.toString(), 20, ReplacingRecord::new);
		}

		@Override
		protected Stream<Pair<Tuple, Tuple>> fetch(Instant lastUpdated) throws IOException {
			if (metadata == null) {
				return Stream.empty();
			}
			return RuntimeSupport.stream(metadata.getAnalysisProvenance())//
					.filter(ap -> ap.getSkip() != null && ap.getSkip()).flatMap(ap -> ap.getIusLimsKeys().stream())//
					.map(iusLimsKey -> {
						final Tuple limsKey = new Tuple(iusLimsKey.getLimsKey().getId(),
								iusLimsKey.getLimsKey().getVersion(), iusLimsKey.getLimsKey().getProvider());
						final IUS originalIUS = metadata.getIUS(iusLimsKey.getIusSWID());
						final Tuple ius = new Tuple(originalIUS.getLane().getSequencerRun().getName(),
								originalIUS.getLane().getLaneIndex().longValue(), originalIUS.getTag());
						return new Pair<>(ius, limsKey);
					});
		}
	}

	static ActionState processingStateToActionState(String state) {
		if (state == null) {
			return ActionState.UNKNOWN;
		}
		switch (WorkflowRunStatus.valueOf(state)) {
		case submitted:
		case submitted_retry:
			return ActionState.WAITING;
		case pending:
			return ActionState.QUEUED;
		case running:
			return ActionState.INFLIGHT;
		case cancelled:
		case submitted_cancel:
		case failed:
			return ActionState.FAILED;
		case completed:
			return ActionState.SUCCEEDED;
		default:
			return ActionState.UNKNOWN;
		}
	}

	private final AnalysisCache analysisCache;
	private Optional<Configuration> configuration = Optional.empty();

	private final UserDefiner definer;

	private String host;

	public Metadata metadata;
	private final ValueCache<Stream<Pair<Tuple, Tuple>>> skipCache;
	private String url;

	public NiassaServer(Path fileName, UserDefiner definer) {
		super(fileName, Configuration.class);
		this.definer = definer;
		analysisCache = new AnalysisCache(fileName);
		skipCache = new SkipLaneCache(fileName);
	}

	public KeyValueCache<Long, Stream<AnalysisState>> analysisCache() {
		return analysisCache;
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Filename", fileName().toString());
				configuration.ifPresent(c -> {
					renderer.line("JAR File", c.getJar());
					renderer.line("Settings", c.getSettings());
					renderer.line("Registered Workflows Count", c.getWorkflows().length);
				});

			}
		};
	}

	public String host() {
		return host;
	}

	public Metadata metadata() {
		return metadata;
	}

	@Override
	protected Optional<Integer> update(Configuration value) {
		// Read the settings
		final Properties settings = new Properties();
		try (InputStream settingsInput = new FileInputStream(value.getSettings())) {
			settings.load(settingsInput);
		} catch (final Exception e) {
			e.printStackTrace();
			return Optional.of(2);
		}
		metadata = new MetadataWS(settings.getProperty("SW_REST_URL"), settings.getProperty("SW_REST_USER"),
				settings.getProperty("SW_REST_PASS"));
		host = settings.getProperty("SW_HOST", host);
		url = settings.getProperty("SW_REST_URL", url);
		analysisCache.invalidateAll();
		skipCache.invalidate();
		definer.clearActions();
		definer.clearFunctions();
		definer.defineFunction(
				RuntimeSupport.removeExtension(fileName(), WorkflowActionRepository.EXTENSION) + "_is_skipped", //
				"Whether an IUS and LIMS key combination has been marked as skipped in " + fileName(), //
				TypeGlue.BOOLEAN, //
				"IUS", TypeGlue.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING), //
				"LIMS key", TypeGlue.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING), //
				(ius, lims) -> skipCache.get()//
						.anyMatch(new Pair<>(ius, lims)::equals)

		);
		for (final WorkflowConfiguration wc : value.getWorkflows()) {
			WorkflowAction.MAX_IN_FLIGHT.putIfAbsent(wc.getAccession(), new Semaphore(wc.getMaxInFlight()));
			wc.define(this, definer, value);
		}
		configuration = Optional.of(value);
		return Optional.empty();
	}

	public String url() {
		return url;
	}
}
