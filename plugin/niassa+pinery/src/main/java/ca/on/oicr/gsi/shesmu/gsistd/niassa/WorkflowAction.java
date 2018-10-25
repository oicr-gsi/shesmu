package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.gsistd.input.Utils;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.definitions.ActionParameter;
import io.prometheus.client.Gauge;
import io.seqware.common.model.WorkflowRunStatus;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.metadata.MetadataWS;

/**
 * Action to run a SeqWare/Niassa workflow
 *
 * The blocking conditions on this are not as strict as for other actions in
 * Shesmu. In particular, two workflows are considered “the same” if they take
 * the same input (either file SWIDs or LIMS information) and have the same
 * workflow accession. <b>The contents of the INI file can be different and they
 * will still be considered the same.</b>
 */
public abstract class WorkflowAction<K extends LimsKey> extends Action {

	private static class AnalysisState implements Comparable<AnalysisState> {
		private final String fileSWIDSToRun;
		private final List<LimsKey> limsKeys;
		private final SortedSet<String> magic;
		private final ActionState state;
		private final long workflowAccession;
		public final int workflowRunAccession;

		public AnalysisState(AnalysisProvenance source) {
			fileSWIDSToRun = source.getWorkflowRunInputFileIds().stream()//
					.map(Object::toString)//
					.collect(Collectors.joining(","));
			limsKeys = source.getIusLimsKeys().stream()//
					.map(IusLimsKey::getLimsKey)//
					.sorted(LIMS_KEY_COMPARATOR)//
					.collect(Collectors.toList());
			state = processingStateToActionState(source.getWorkflowRunStatus());
			workflowAccession = source.getWorkflowId();
			workflowRunAccession = source.getWorkflowRunId();
			magic = source.getWorkflowRunAttributes().getOrDefault("magic", Collections.emptySortedSet());
		}

		/**
		 * Sort so that the latest, most successful run is first.
		 */
		@Override
		public int compareTo(AnalysisState other) {
			int comparison = state.sortPriority() - other.state.sortPriority();
			if (comparison == 0) {
				comparison = -Integer.compare(other.workflowRunAccession, workflowRunAccession);
			}
			return comparison;
		}
	}

	private static final Cache<Long, List<AnalysisState>> CACHE = new Cache<Long, List<AnalysisState>>("sqw-analysis",
			20) {

		@Override
		protected List<AnalysisState> fetch(Long key) throws IOException {
			final Map<FileProvenanceFilter, Set<String>> filters = new EnumMap<>(FileProvenanceFilter.class);
			filters.put(FileProvenanceFilter.workflow, Collections.singleton(Long.toString(key)));
			return client.getAnalysisProvenance(filters).stream()//
					.filter(ap -> ap.getWorkflowId() != null && (ap.getSkip() == null || !ap.getSkip()))//
					.map(AnalysisState::new)//
					.collect(Collectors.toList());
		}
	};
	private static final DefaultProvenanceClient client = new DefaultProvenanceClient();

	private static final Pattern COMMA = Pattern.compile(",");

	protected static final Comparator<LimsKey> LIMS_KEY_COMPARATOR = Comparator.comparing(LimsKey::getProvider)//
			.thenComparing(Comparator.comparing(LimsKey::getId))//
			.thenComparing(Comparator.comparing(LimsKey::getVersion));

	static final Map<Long, Semaphore> MAX_IN_FLIGHT = new HashMap<>();

	private static final Pattern RUN_SWID_LINE = Pattern.compile(".*Created workflow run with SWID: (\\d+).*");

	private static final Gauge runCreated = Gauge
			.build("shesmu_niassa_run_created", "The number of workflow runs launched.")
			.labelNames("target", "workflow").create();

	private static final Gauge runFailed = Gauge
			.build("shesmu_niassa_run_failed", "The number of workflow runs that failed to be launched.")
			.labelNames("target", "workflow").create();

	static {
		Utils.LOADER.ifPresent(loader -> {
			Utils.setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
			Utils.setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
			Utils.setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
		});

	}

	private static ActionState processingStateToActionState(String state) {
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

	private static void repackIntegers(ObjectNode node, String name, String ids) {
		COMMA.splitAsStream(ids).map(Long::parseUnsignedLong).sorted().forEach(node.putArray(name)::add);
	}

	private String fileAccessions;

	private final Set<Integer> fileSwids = new TreeSet<>();

	private boolean first = true;

	private long id;

	private boolean inflight = false;

	@RuntimeInterop
	public Properties ini = new Properties();

	protected final String jarPath;

	private String magic = "0";

	private String parentAccessions;

	private final Set<Integer> parentSwids = new TreeSet<>();

	private final long[] previousAccessions;

	private int runAccession;

	private final Set<String> services;

	private final String settingsPath;

	private int waitForSomeoneToRerunIt;

	private final long workflowAccession;

	public WorkflowAction(long workflowAccession, long[] previousAccessions, String jarPath, String settingsPath,
			String[] services) {
		super("niassa");
		this.workflowAccession = workflowAccession;
		this.previousAccessions = previousAccessions;
		this.jarPath = jarPath;
		this.settingsPath = settingsPath;
		this.services = Stream.of(services).collect(Collectors.toSet());
	}

	@RuntimeInterop
	public final String addFileSwid(String id) {
		fileSwids.add(Integer.parseUnsignedInt(id));
		return id;
	}

	@RuntimeInterop
	public final String addProcessingSwid(String id) {
		parentSwids.add(Integer.parseUnsignedInt(id));
		return id;
	}

	private boolean compare(AnalysisState state) {
		if (state.workflowAccession != workflowAccession
				&& Arrays.binarySearch(previousAccessions, state.workflowAccession) < 0
				|| !state.magic.isEmpty() && !state.magic.contains(magic)
				|| !state.fileSWIDSToRun.equals(fileAccessions) || state.limsKeys.size() != limsKeys().size()) {
			return false;
		}
		for (int i = 0; i < limsKeys().size(); i++) {
			final LimsKey a = state.limsKeys.get(i);
			final LimsKey b = limsKeys().get(i);
			if (!a.getProvider().equals(b.getProvider()) || !a.getId().equals(b.getId())
					|| !a.getVersion().equals(b.getVersion())
					|| !a.getLastModified().toInstant().equals(b.getLastModified().toInstant())) {
				return false;
			}
		}
		return true;
	}

	@Override
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final WorkflowAction<?> other = (WorkflowAction<?>) obj;
		if (fileAccessions == null) {
			if (other.fileAccessions != null) {
				return false;
			}
		} else if (!fileAccessions.equals(other.fileAccessions)) {
			return false;
		}
		if (jarPath == null) {
			if (other.jarPath != null) {
				return false;
			}
		} else if (!jarPath.equals(other.jarPath)) {
			return false;
		}
		if (limsKeys() == null) {
			if (other.limsKeys() != null) {
				return false;
			}
		} else if (!limsKeys().equals(other.limsKeys())) {
			return false;
		}
		if (magic == null) {
			if (other.magic != null) {
				return false;
			}
		} else if (!magic.equals(other.magic)) {
			return false;
		}
		if (settingsPath == null) {
			if (other.settingsPath != null) {
				return false;
			}
		} else if (!settingsPath.equals(other.settingsPath)) {
			return false;
		}
		if (workflowAccession != other.workflowAccession) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (fileAccessions == null ? 0 : fileAccessions.hashCode());
		result = prime * result + (jarPath == null ? 0 : jarPath.hashCode());
		result = prime * result + (limsKeys() == null ? 0 : limsKeys().hashCode());
		result = prime * result + (magic == null ? 0 : magic.hashCode());
		result = prime * result + (settingsPath == null ? 0 : settingsPath.hashCode());
		result = prime * result + (int) (workflowAccession ^ workflowAccession >>> 32);
		return result;
	}

	protected List<K> limsKeys() {
		return Collections.emptyList();
	}

	@ActionParameter
	public final void magic(String magic) {
		if (!magic.isEmpty()) {
			this.magic = magic;
		}
	}

	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	@Override
	public final ActionState perform() {
		if (Throttler.anyOverloaded(services)) {
			return ActionState.THROTTLED;
		}
		try {
			if (runAccession != 0) {
				final Map<FileProvenanceFilter, Set<String>> query = new EnumMap<>(FileProvenanceFilter.class);
				query.put(FileProvenanceFilter.workflow_run, Collections.singleton(Integer.toString(runAccession)));
				final ActionState state = client.getAnalysisProvenance(query).stream().findFirst()//
						.map(ap -> processingStateToActionState(ap.getWorkflowRunStatus()))//
						.orElse(ActionState.UNKNOWN);
				if (state == ActionState.FAILED && ++waitForSomeoneToRerunIt > 5) {
					// We've previously found or created a run accession for this action, but it's
					// been in a failed state. We're really hoping that someone will manually rerun
					// it, but that new version will have a separate workflow run SWID, so we need
					// to forget our old ID and then find a new one.
					runAccession = 0;
					waitForSomeoneToRerunIt = 0;
				}
				return state;
			}
			// Read the FPR cache to determine if this workflow has already been run
			final Optional<AnalysisState> current = workflowAccessions()//
					.boxed()//
					.flatMap(CACHE::flatGet)//
					.flatMap(l -> l.stream()//
							.filter(this::compare))//
					.sorted()//
					.findFirst();
			if (current.isPresent()) {
				runAccession = current.get().workflowRunAccession;
				final ActionState state = current.get().state;
				final boolean isDone = state == ActionState.SUCCEEDED || state == ActionState.FAILED;
				if (inflight && isDone) {
					inflight = false;
					MAX_IN_FLIGHT.get(workflowAccession).release();
				} else if (!inflight && !isDone) {
					// This is the case where the server has restarted and we find our Niassa job
					// is already running, but we haven't counted in our max-in-flight, so, we keep
					// trying to acquire the lock. To prevent other jobs from acquiring all the
					// locks, no Niassa job will start until the others have been queried at least
					// once.
					// We can overrun the max-in-flight by r*m where r is the number of Shesmu
					// restarts since any workflow last finished and m is the max-in-flight, but we
					// work hard to make this a worst-case scenario.
					if (MAX_IN_FLIGHT.get(workflowAccession).tryAcquire()) {
						inflight = true;
					}
				}
				return state;
			}

			// This is to avoid overruning the max-in-flight after a restart by giving all
			// actions a chance to check if they are already running and acquire a lock.
			if (first) {
				first = false;
				return ActionState.WAITING;
			}

			if (!MAX_IN_FLIGHT.get(workflowAccession).tryAcquire()) {
				return ActionState.WAITING;
			}

			// Read the settings
			final Properties settings = new Properties();
			try (InputStream settingsInput = new FileInputStream(settingsPath)) {
				settings.load(settingsInput);
			}
			// Create any IUS accessions required and update the INI file based on those
			final Metadata metadata = new MetadataWS(settings.getProperty("SW_REST_URL"),
					settings.getProperty("SW_REST_USER"), settings.getProperty("SW_REST_PASS"));
			final List<Pair<Integer, K>> iusLimsKeys = limsKeys().stream()//
					.map(key -> new Pair<>(metadata.addIUS(metadata.addLimsKey(key.getProvider(), key.getId(),
							key.getVersion(), key.getLastModified()), false), key))//
					.collect(Collectors.toList());
			prepareIniForLimsKeys(iusLimsKeys.stream());
			final String iusAccessions = iusLimsKeys.stream()//
					.map(Pair::first)//
					.sorted()//
					.map(Object::toString)//
					.collect(Collectors.joining(","));

			final File iniFile = File.createTempFile("niassa", ".ini");
			iniFile.deleteOnExit();
			try (OutputStream out = new FileOutputStream(iniFile)) {
				ini.store(out, String.format("Generated by Shesmu for workflow %d", workflowAccession));
			}

			// Create a command line to invoke the workflow scheduler
			final ArrayList<String> runArgs = new ArrayList<>();
			runArgs.add("java");
			runArgs.add("-jar");
			runArgs.add(jarPath);
			runArgs.add("--plugin");
			runArgs.add("io.seqware.pipeline.plugins.WorkflowScheduler");
			runArgs.add("--");
			runArgs.add("--workflow-accession");
			runArgs.add(Long.toString(workflowAccession));
			runArgs.add("--ini-files");
			runArgs.add(iniFile.getAbsolutePath());
			if (!fileAccessions.isEmpty()) {
				runArgs.add("--input-files");
				runArgs.add(fileAccessions);
			}
			if (!parentAccessions.isEmpty()) {
				runArgs.add("--parent-accessions");
				runArgs.add(parentAccessions);
			}
			if (!iusAccessions.isEmpty()) {
				runArgs.add("--link-workflow-run-to-parents");
				runArgs.add(iusAccessions);
			}
			runArgs.add("--host");
			runArgs.add(settings.getProperty("SW_HOST"));
			final ProcessBuilder builder = new ProcessBuilder(runArgs);
			builder.environment().put("SEQWARE_SETTINGS", settingsPath);
			builder.environment().remove("CLASSPATH");
			final Process process = builder.start();
			runAccession = 0;
			boolean success = true;
			try (OutputStream stdin = process.getOutputStream();
					Scanner stdout = new Scanner(process.getInputStream());
					InputStream stderr = process.getErrorStream()) {
				final String line = stdout.useDelimiter("\\Z").next();
				final Matcher matcher = RUN_SWID_LINE.matcher(line);
				if (matcher.matches()) {
					runAccession = Integer.parseUnsignedInt(matcher.group(1));
				} else {
					System.err.printf("Failed to get workflow run accession for Niassa job for workflow %d\n",
							workflowAccession);
					success = false;
				}
			}
			final int scheduleExitCode = process.waitFor();
			if (scheduleExitCode != 0) {
				System.err.printf("Failed to schedule Niassa workflow %d: exited %d\n", workflowAccession,
						scheduleExitCode);
			}
			success &= scheduleExitCode == 0;
			if (success) {
				workflowAccessions().forEach(CACHE::invalidate);
				final ArrayList<String> annotationArgs = new ArrayList<>();
				annotationArgs.add("java");
				annotationArgs.add("-jar");
				annotationArgs.add(jarPath);
				annotationArgs.add("--plugin");
				annotationArgs.add("net.sourceforge.seqware.pipeline.plugins.AttributeAnnotator");
				annotationArgs.add("--");
				annotationArgs.add("--workflow-run-accession");
				annotationArgs.add(Long.toString(runAccession));
				annotationArgs.add("--key");
				annotationArgs.add("magic");
				annotationArgs.add("--value");
				annotationArgs.add(magic);
				final ProcessBuilder annotationBuilder = new ProcessBuilder(annotationArgs);
				annotationBuilder.environment().put("SEQWARE_SETTINGS", settingsPath);
				annotationBuilder.environment().remove("CLASSPATH");
				final Process annotationProcess = annotationBuilder.start();
				annotationProcess.getInputStream().close();
				annotationProcess.getOutputStream().close();
				annotationProcess.getErrorStream().close();
				final int annotationExitCode = annotationProcess.waitFor();
				if (annotationExitCode != 0) {
					System.err.printf("Failed to annotate Niassa workflow run %d: exited %d\n", runAccession,
							annotationExitCode);
				}

				success = annotationExitCode == 0;
			}

			// Indicate if we managed to schedule the workflow; if we did, mark ourselves
			// dirty so there is a delay before our next query.
			(success ? runCreated : runFailed)
					.labels(settings.getProperty("SW_REST_URL"), Long.toString(workflowAccession)).inc();
			return success ? ActionState.QUEUED : ActionState.FAILED;
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}
	}

	@Override
	public final void prepare() {
		fileAccessions = fileSwids.stream().sorted().map(Object::toString).collect(Collectors.joining(","));
		parentAccessions = parentSwids.stream().sorted().map(Object::toString).collect(Collectors.joining(","));
	}

	protected void prepareIniForLimsKeys(Stream<Pair<Integer, K>> stream) {
		// Do nothing.
	}

	@Override
	public final int priority() {
		return 0;
	}

	@Override
	public final long retryMinutes() {
		return 10;
	}

	@Override
	public final ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("magic", magic);
		limsKeys().stream().map(k -> {
			final ObjectNode key = mapper.createObjectNode();
			key.put("provider", k.getProvider());
			key.put("id", k.getId());
			key.put("version", k.getVersion());
			key.put("lastModified", DateTimeFormatter.ISO_INSTANT.format(k.getLastModified()));
			return key;
		}).forEach(node.putArray("limsKeys")::add);
		final ObjectNode iniJson = node.putObject("ini");
		ini.entrySet().stream().forEach(e -> iniJson.put(e.getKey().toString(), e.getValue().toString()));
		node.put("workflowAccession", workflowAccession);
		node.put("workflowRunAccession", runAccession);
		node.put("jarPath", jarPath);
		node.put("settingsPath", settingsPath);
		if (id != 0) {
			node.put("workflowRunId", id);
		}
		repackIntegers(node, "fileAccessions", fileAccessions);
		repackIntegers(node, "parentAccessions", parentAccessions);
		final ObjectNode iniNode = node.putObject("ini");
		ini.forEach((k, v) -> iniNode.put(k.toString(), v.toString()));
		limsKeys().stream().map(k -> {
			final ObjectNode key = mapper.createObjectNode();
			key.put("provider", k.getProvider());
			key.put("id", k.getId());
			key.put("version", k.getVersion());
			key.put("lastModified", k.getLastModified().toEpochSecond());
			return key;
		}).forEach(node.putArray("limsKeys")::add);

		return node;
	}

	private LongStream workflowAccessions() {
		return LongStream.concat(LongStream.of(workflowAccession), LongStream.of(previousAccessions));
	}

}
