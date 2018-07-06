package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.Cache;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import io.prometheus.client.Gauge;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.metadata.MetadataWS;

/**
 * Action to run a SeqWare workflow
 *
 * The blocking conditions on this are not as strict as for other actions in
 * Shesmu. In particular, two workflows are considered “the same” if they take
 * the same input (either file SWIDs or LIMS information) and have the same
 * workflow accession. <b>The contents of the INI file can be different and they
 * will still be considered the same.</b>
 */
public class SeqWareWorkflowAction extends Action {

	static final Map<Long, Semaphore> MAX_IN_FLIGHT = new HashMap<>();

	private static class AnalysisState {
		private final String fileSWIDSToRun;
		private final List<LimsKey> limsKeys;
		private final ActionState state;
		private final long workflowAccession;
		private final SortedSet<String> magic;

		public AnalysisState(AnalysisProvenance source) {
			fileSWIDSToRun = source.getWorkflowRunInputFileIds().stream()//
					.map(Object::toString)//
					.collect(Collectors.joining(","));
			limsKeys = source.getIusLimsKeys().stream()//
					.map(IusLimsKey::getLimsKey)//
					.sorted(LIMS_KEY_COMPARATOR)//
					.collect(Collectors.toList());
			state = processingStateToActionState(source.getProcessingStatus());
			workflowAccession = source.getWorkflowId();
			magic = source.getWorkflowRunAttributes().getOrDefault("magic", Collections.emptySortedSet());
		}
	}

	private static final Type A_SET_TYPE = Type.getType(Set.class);
	private static final Type A_LONG_ARRAY_TYPE = Type.getType(long[].class);
	private static final Type A_SQWACTION_TYPE = Type.getType(SeqWareWorkflowAction.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Cache<String, List<AnalysisState>> CACHE = new Cache<String, List<AnalysisState>>(
			"sqw-analysis", 20) {

		@Override
		protected List<AnalysisState> fetch(String key) throws IOException {
			return client.getAnalysisProvenance().stream().map(AnalysisState::new).collect(Collectors.toList());
		}
	};

	private static final DefaultProvenanceClient client = new DefaultProvenanceClient();

	private static final Pattern COMMA = Pattern.compile(",");

	protected static final Comparator<LimsKey> LIMS_KEY_COMPARATOR = Comparator.comparing(LimsKey::getProvider)//
			.thenComparing(Comparator.comparing(LimsKey::getId))//
			.thenComparing(Comparator.comparing(LimsKey::getVersion));

	private static final Gauge runCreated = Gauge
			.build("shesmu_seqware_run_created", "The number of workflow runs launched.")
			.labelNames("target", "workflow").create();

	private static final Gauge runFailed = Gauge
			.build("shesmu_seqware_run_failed", "The number of workflow runs that failed to be launched.")
			.labelNames("target", "workflow").create();

	private static final Method SQWACTION__CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] { Type.LONG_TYPE,
			A_LONG_ARRAY_TYPE, A_STRING_TYPE, A_STRING_TYPE, Type.getType(String[].class) });
	private static final Method SQWACTION__PREPARE = new Method("prepare", Type.VOID_TYPE, new Type[] {});

	static {
		Utils.LOADER.ifPresent(loader -> {
			Utils.setProvider(loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
			Utils.setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
			Utils.setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
		});

	}

	/**
	 * Create a new workflow flow definition.
	 * 
	 * @param name
	 *            the Shesmu “Run” name of the workflow
	 * @param type
	 *            the ASM type of the class implementing the custom LIMS key
	 *            handling
	 * @param workflowAccession
	 *            the SeqWare accession of the workflow
	 * @param jarPath
	 *            the path to the SeqWare distribution JAR
	 * @param settingsPath
	 *            the path to the SeqWare settings file
	 * @param services
	 *            the throttler services to engage for this workflow
	 * @param parameters
	 *            the parameters accepted by this workflow
	 */
	public static ActionDefinition create(String name, Type type, long workflowAccession, long[] previousAccessions,
			String jarPath, String settingsPath, String[] services, Stream<SeqWareParameterDefinition> parameters) {
		return new ActionDefinition(name, type,
				Stream.concat(Stream.of(ParameterDefinition.forField(A_SQWACTION_TYPE, "magic", Imyhat.STRING, false)),
						parameters.map(p -> p.generate(type)))) {

			@Override
			public void finalize(GeneratorAdapter methodGen, int actionLocal) {
				methodGen.loadLocal(actionLocal);
				methodGen.invokeVirtual(type, SQWACTION__PREPARE);
			}

			@Override
			public void initialize(GeneratorAdapter methodGen) {
				methodGen.newInstance(type);
				methodGen.dup();
				methodGen.push(workflowAccession);
				methodGen.push(previousAccessions.length);
				methodGen.newArray(Type.LONG_TYPE);
				Arrays.sort(previousAccessions);
				for (int i = 0; i < previousAccessions.length; i++) {
					methodGen.dup();
					methodGen.push(i);
					methodGen.push(previousAccessions[i]);
					methodGen.arrayStore(Type.LONG_TYPE);
				}
				methodGen.push(jarPath);
				methodGen.push(settingsPath);
				methodGen.push(services.length);
				methodGen.newArray(A_STRING_TYPE);
				for (int i = 0; i < services.length; i++) {
					methodGen.dup();
					methodGen.push(i);
					methodGen.push(services[i]);
					methodGen.arrayStore(A_STRING_TYPE);
				}
				methodGen.invokeConstructor(type, SQWACTION__CTOR);
			}
		};
	}

	/**
	 * Create a <tt>lanes</tt> parameter that can tie a workflow run to new IUSes
	 * 
	 * To use this, create a class that inherits from {@link SeqWareWorkflowAction}
	 * that has a constructor that exactly matches the superclass constructor.
	 * <ol>
	 * <li>Create a new method called <tt>void lanes(Set&lt;Tuple&gt; info)</tt>
	 * that accepts a set of tuples as described by the <tt>lanes</tt> parameter and
	 * stores that information in a field.</li>
	 * <li>Override the {@link #limsKeys()} to return a collection of
	 * {@link LimsKey} based on this stored information</li>
	 * <li>Override {@link #prepareIniForLimsKeys(Stream)} to take a stream of
	 * {@link IusLimsKey} and update {@link #ini} as appropriate. The
	 * {@link LimsKey} values stored in the {@link IusLimsKey} are the ones provided
	 * earlier, so they can be cast to a class carrying supplemental
	 * information</li>
	 * </ol>
	 * 
	 * @return
	 */
	public static final SeqWareParameterDefinition lanes(Imyhat... lanes) {
		return type -> new ParameterDefinition() {

			@Override
			public String name() {
				return "lanes";
			}

			@Override
			public boolean required() {
				return true;
			}

			@Override
			public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
				renderer.methodGen().loadLocal(actionLocal);
				loadParameter.accept(renderer);
				renderer.methodGen().invokeVirtual(type,
						new Method("lanes", Type.VOID_TYPE, new Type[] { A_SET_TYPE }));
			}

			@Override
			public Imyhat type() {
				return Imyhat.tuple(lanes).asList();
			}
		};
	}

	@RuntimeInterop
	public String magic = "";

	private static ActionState processingStateToActionState(String state) {
		switch (state) {
		case "pending":
			return ActionState.QUEUED;
		case "running":
			return ActionState.INFLIGHT;
		case "failed":
			return ActionState.FAILED;
		case "success":
			return ActionState.SUCCEEDED;
		default:
			return ActionState.UNKNOWN;
		}
	}

	private static void repackIntegers(ObjectNode node, String name, String ids) {
		COMMA.splitAsStream(ids).map(Long::parseUnsignedLong).sorted().forEach(node.putArray(name)::add);
	}

	private boolean dirty = false;

	private boolean first = true;

	private long id;

	@RuntimeInterop
	public Properties ini = new Properties();

	private String inputFiles;

	private final Set<Integer> inputSwids = new TreeSet<>();
	protected final String jarPath;

	private final String settingsPath;

	private final long workflowAccession;
	private final long[] previousAccessions;
	private final Set<String> services;

	public SeqWareWorkflowAction(long workflowAccession, long[] previousAccessions, String jarPath, String settingsPath,
			String[] services) {
		super("seqware");
		this.workflowAccession = workflowAccession;
		this.previousAccessions = previousAccessions;
		this.jarPath = jarPath;
		this.settingsPath = settingsPath;
		this.services = Stream.of(services).collect(Collectors.toSet());
	}

	@RuntimeInterop
	public String addSwid(String id) {
		inputSwids.add(Integer.parseUnsignedInt(id));
		return id;
	}

	private boolean compare(AnalysisState state) {
		if ((state.workflowAccession != workflowAccession
				&& Arrays.binarySearch(previousAccessions, state.workflowAccession) < 0)
				|| !state.magic.isEmpty() && !state.magic.contains(magic) || !state.fileSWIDSToRun.equals(inputFiles)
				|| state.limsKeys.size() != limsKeys().size()) {
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
		final SeqWareWorkflowAction other = (SeqWareWorkflowAction) obj;
		if (inputFiles == null) {
			if (other.inputFiles != null) {
				return false;
			}
		} else if (!inputFiles.equals(other.inputFiles)) {
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
		result = prime * result + (inputFiles == null ? 0 : inputFiles.hashCode());
		result = prime * result + (jarPath == null ? 0 : jarPath.hashCode());
		result = prime * result + (limsKeys() == null ? 0 : limsKeys().hashCode());
		result = prime * result + (magic == null ? 0 : magic.hashCode());
		result = prime * result + (settingsPath == null ? 0 : settingsPath.hashCode());
		result = prime * result + (int) (workflowAccession ^ workflowAccession >>> 32);
		return result;
	}

	protected List<LimsKey> limsKeys() {
		return Collections.emptyList();
	}

	private static final Pattern RUN_SWID_LINE = Pattern.compile("Created workflow run with SWID: (\\d+)");

	private boolean inflight = false;

	@Override
	public final ActionState perform() {
		if (Throttler.anyOverloaded(services)) {
			return ActionState.THROTTLED;
		}
		try {
			// Read the FPR cache to determine if this workflow has already been run
			final Optional<AnalysisState> current = CACHE.get(settingsPath)//
					.flatMap(l -> l.stream()//
							.filter(this::compare)//
							.findFirst());
			if (current.isPresent()) {
				dirty = false;
				ActionState state = current.get().state;
				boolean isDone = state == ActionState.SUCCEEDED || state == ActionState.FAILED;
				if (inflight && isDone) {
					inflight = false;
					MAX_IN_FLIGHT.get(workflowAccession).release();
				} else if (!inflight && !isDone) {
					// This is the case where the server has restarted and we find our SeqWare job
					// is already running, but we haven't counted in our max-in-flight, so, we keep
					// trying to acquire the lock. To prevent other jobs from acquiring all the
					// locks, no SeqWare job will start until the others have been queried at least
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
			final File iniFile = File.createTempFile("seqware", ".ini");
			try (OutputStream out = new FileOutputStream(iniFile)) {
				ini.store(out, String.format("Generated by Shesmu for workflow %d", workflowAccession));
			}

			// Create any IUS accessions required and update the INI file based on those
			final Metadata metadata = new MetadataWS(settings.getProperty("SW_REST_URL"),
					settings.getProperty("SW_REST_USER"), settings.getProperty("SW_REST_PASS"));
			final List<IusLimsKey> iusLimsKeys = limsKeys().stream()//
					.map(key -> {
						final int iusAccession = metadata.addIUS(metadata.addLimsKey(key.getProvider(), key.getId(),
								key.getVersion(), key.getLastModified()), false);

						return new IusLimsKey() {

							@Override
							public Integer getIusSWID() {
								return iusAccession;
							}

							@Override
							public LimsKey getLimsKey() {
								return key;
							}
						};
					})//
					.collect(Collectors.toList());
			prepareIniForLimsKeys(iusLimsKeys.stream());
			final String iusAccessions = iusLimsKeys.stream()//
					.map(IusLimsKey::getIusSWID).sorted()//
					.map(Object::toString)//
					.collect(Collectors.joining(","));

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
			runArgs.add("--input-files");
			runArgs.add(inputFiles);// empty or ?
			runArgs.add("--parent-accessions");
			runArgs.add(Stream.of(iusAccessions, inputFiles)//
					.filter(x -> !x.isEmpty())//
					.collect(Collectors.joining(",")));
			if (!inputFiles.isEmpty()) {
				runArgs.add("--link-workflow-run-to-parents");
				runArgs.add(inputFiles);
			}
			runArgs.add("--host");
			runArgs.add(settings.getProperty("SW_REST_URL"));
			final ProcessBuilder builder = new ProcessBuilder(runArgs);
			builder.environment().put("SEQWARE_SETTINGS", settingsPath);
			final Process process = builder.start();
			int runAccession = 0;
			boolean success = true;
			try (OutputStream stdin = process.getOutputStream();
					Scanner stdout = new Scanner(process.getInputStream());
					InputStream stderr = process.getErrorStream()) {
				String line = stdout.findWithinHorizon(RUN_SWID_LINE, 0);
				if (line == null) {
					success = false;
				} else {
					runAccession = Integer.parseUnsignedInt(RUN_SWID_LINE.matcher(line).group(1));
				}
			}
			success &= process.waitFor() == 0;
			if (success) {
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
				annotationArgs.add("--host");
				annotationArgs.add(settings.getProperty("SW_REST_URL"));
				final ProcessBuilder annotationBuilder = new ProcessBuilder(annotationArgs);
				annotationBuilder.environment().put("SEQWARE_SETTINGS", settingsPath);
				final Process annotationProcess = annotationBuilder.start();
				annotationProcess.getInputStream().close();
				annotationProcess.getOutputStream().close();
				annotationProcess.getErrorStream().close();
				success = annotationProcess.waitFor() == 0;
			}

			// Indicate if we managed to schedule the workflow; if we did, mark ourselves
			// dirty so there is a delay before our next query.
			(success ? runCreated : runFailed)
					.labels(settings.getProperty("SW_REST_URL"), Long.toString(workflowAccession)).inc();
			dirty = true;
			return success ? ActionState.QUEUED : ActionState.FAILED;
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}
	}

	@RuntimeInterop
	public final void prepare() {
		inputFiles = inputSwids.stream().sorted().map(Object::toString).collect(Collectors.joining(","));
	}

	protected void prepareIniForLimsKeys(Stream<IusLimsKey> stream) {
		// Do nothing.
	}

	@Override
	public final int priority() {
		return 0;
	}

	@Override
	public final long retryMinutes() {
		return dirty ? 38 : 20;
	}

	@Override
	public final ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("type", "seqware");
		node.put("workflowAccession", workflowAccession);
		node.put("jarPath", jarPath);
		node.put("settingsPath", settingsPath);
		if (id != 0) {
			node.put("workflowRunId", id);
		}
		repackIntegers(node, "inputFiles", inputFiles);
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

}
