package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import io.seqware.Engines;
import io.seqware.pipeline.SqwKeys;
import io.seqware.pipeline.api.Scheduler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.sourceforge.seqware.common.model.WorkflowRunAttribute;

/**
 * Action to run a SeqWare/Niassa workflow
 *
 * <p>The blocking conditions on this are not as strict as for other actions in Shesmu. In
 * particular, two workflows are considered “the same” if they take the same input (either file
 * SWIDs or LIMS information) and have the same workflow accession. <b>The contents of the INI file
 * can be different and they will still be considered the same.</b>
 */
public final class WorkflowAction extends Action {

  static final Comparator<LimsKey> LIMS_KEY_COMPARATOR =
      Comparator.comparing(LimsKey::getProvider)
          .thenComparing(LimsKey::getId)
          .thenComparing(LimsKey::getVersion);

  static final Map<Long, Pair<AtomicInteger, AtomicInteger>> MAX_IN_FLIGHT =
      new ConcurrentHashMap<>();

  private static final Gauge runCreated =
      Gauge.build("shesmu_niassa_run_created", "The number of workflow runs launched.")
          .labelNames("target", "workflow")
          .create();

  private static final Gauge runFailed =
      Gauge.build(
              "shesmu_niassa_run_failed", "The number of workflow runs that failed to be launched.")
          .labelNames("target", "workflow")
          .create();

  private String fileAccessions;

  private final Set<Integer> fileSwids = new TreeSet<>();

  private boolean first = true;
  private boolean hasLaunched;
  private boolean inflight = false;
  Properties ini = new Properties();
  private final LanesType lanesType;
  private List<StringableLimsKey> limsKeys = Collections.emptyList();
  private long majorOliveVersion;
  private final Set<Integer> parentSwids = new TreeSet<>();
  private final long[] previousAccessions;
  private int runAccession;
  private final Supplier<NiassaServer> server;
  private final Set<String> services;
  private int waitForSomeoneToRerunIt;
  private final long workflowAccession;

  public WorkflowAction(
      Supplier<NiassaServer> server,
      LanesType laneType,
      long workflowAccession,
      long[] previousAccessions,
      String[] services) {
    super("niassa");
    this.server = server;
    this.lanesType = laneType;
    this.workflowAccession = workflowAccession;
    this.previousAccessions = previousAccessions;
    this.services = Stream.of(services).collect(Collectors.toSet());
  }

  @Override
  public void accepted() {
    MAX_IN_FLIGHT.get(workflowAccession).first().decrementAndGet();
  }

  final void addFileSwid(String id) {
    fileSwids.add(Integer.parseUnsignedInt(id));
  }

  final void addProcessingSwid(String id) {
    parentSwids.add(Integer.parseUnsignedInt(id));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WorkflowAction that = (WorkflowAction) o;
    return majorOliveVersion == that.majorOliveVersion
        && workflowAccession == that.workflowAccession
        && Objects.equals(fileSwids, that.fileSwids)
        && Objects.equals(parentSwids, that.parentSwids)
        && Objects.equals(limsKeys, that.limsKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileSwids, majorOliveVersion, parentSwids, workflowAccession, limsKeys);
  }

  void lanes(Set<?> input) {
    limsKeys =
        input
            .stream()
            .map(lanesType::makeLimsKey)
            .sorted(LIMS_KEY_COMPARATOR)
            .collect(Collectors.toList());
  }

  @ActionParameter(name = "major_olive_version")
  public final void majorOliveVersion(long majorOliveVersion) {
    this.majorOliveVersion = majorOliveVersion;
  }

  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  public final ActionState perform(ActionServices actionServices) {
    if (actionServices.isOverloaded(services)) {
      return ActionState.THROTTLED;
    }
    // If this is the first time we're called, then consult the startup counter to determine if we
    // should attempt to launch; if a pile of actions are created on startup, we will consult the
    // analysis provenance and, if we find nothing, yield, so that other actions can find
    // potentially in-flight jobs and update the max-in-flight lock.
    final boolean shouldWait =
        first && MAX_IN_FLIGHT.get(workflowAccession).first().incrementAndGet() < 1;
    first = false;
    try {
      // If we know our workflow run, check its status
      if (runAccession != 0) {
        final Map<FileProvenanceFilter, Set<String>> query =
            new EnumMap<>(FileProvenanceFilter.class);
        query.put(
            FileProvenanceFilter.workflow_run,
            Collections.singleton(Integer.toString(runAccession)));
        final ActionState state =
            server
                .get()
                .metadata()
                .getAnalysisProvenance(query)
                .stream()
                .findFirst()
                .map(ap -> NiassaServer.processingStateToActionState(ap.getWorkflowRunStatus()))
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
      // Read the analysis provenance cache to determine if this workflow has already been run
      final Optional<AnalysisState> current =
          workflowAccessions()
              .boxed()
              .flatMap(
                  accession ->
                      server
                          .get()
                          .analysisCache()
                          .get(accession)
                          .filter(
                              as ->
                                  as.compare(
                                      workflowAccessions(),
                                      Long.toString(majorOliveVersion),
                                      fileAccessions,
                                      limsKeys)))
              .sorted()
              .findFirst();
      if (current.isPresent()) {
        // We found a matching workflow run in analysis provenance
        runAccession = current.get().workflowRunAccession();
        final ActionState state = current.get().state();
        final boolean isDone = state == ActionState.SUCCEEDED || state == ActionState.FAILED;
        if (inflight && isDone) {
          // If we've transitioned from a running state to a non-running state, then release a
          // max-in-flight lock
          inflight = false;
          MAX_IN_FLIGHT.get(workflowAccession).second().incrementAndGet();
        } else if (!inflight && !isDone) {
          // This is the case where the server has restarted and we find our Niassa job
          // is already running, but we haven't counted in our max-in-flight, so, we keep
          // trying to acquire the lock. To prevent other jobs from acquiring all the
          // locks, no Niassa job will start until the others have been queried at least
          // once.
          // We can overrun the max-in-flight by r*m where r is the number of Shesmu
          // restarts since any workflow last finished and m is the max-in-flight, but we
          // work hard to make this a worst-case scenario.
          MAX_IN_FLIGHT.get(workflowAccession).second().decrementAndGet();
          inflight = true;
        }
        return state;
      }

      // This is to avoid overrunning the max-in-flight after a restart by giving all
      // actions a chance to check if they are already running and acquire a lock. Once a server
      // reaches steady state, new actions shouldn't have to wait.
      if (shouldWait) {
        return ActionState.WAITING;
      }
      // We get exactly one attempt to launch a job. If we fail, that's the end of this action. A
      // human needs to rescue us.
      if (hasLaunched) {
        return ActionState.FAILED;
      }

      // Try to decrement the max-in-flight lock; if it was previously greater than zero, then we
      // can launch; decrement the lock if there was capacity; otherwise, leave it alone
      if (MAX_IN_FLIGHT.get(workflowAccession).second().getAndUpdate(x -> x < 1 ? x : x - 1) > 0) {
        return ActionState.WAITING;
      }

      final List<String> iusAccessions;
      if (lanesType != null) {
        final List<Pair<Integer, StringableLimsKey>> iusLimsKeys =
            limsKeys
                .stream()
                .map(
                    key ->
                        new Pair<>(
                            server
                                .get()
                                .metadata()
                                .addIUS(
                                    server
                                        .get()
                                        .metadata()
                                        .addLimsKey(
                                            key.getProvider(),
                                            key.getId(),
                                            key.getVersion(),
                                            key.getLastModified()),
                                    false),
                            key))
                .collect(Collectors.toList());
        ini.setProperty(
            "lanes",
            iusLimsKeys
                .stream()
                .map(p -> p.second().asLaneString(p.first()))
                .collect(Collectors.joining(lanesType.delimiter())));
        iusAccessions =
            iusLimsKeys
                .stream()
                .map(Pair::first)
                .sorted()
                .map(Object::toString)
                .collect(Collectors.toList());
      } else {
        iusAccessions = Collections.emptyList();
      }

      final List<String> fileLimsKeyAccessions =
          fileSwids
              .stream()
              .flatMap(
                  fileSwid -> {
                    final Map<FileProvenanceFilter, Set<String>> query =
                        new EnumMap<>(FileProvenanceFilter.class);
                    query.put(
                        FileProvenanceFilter.file,
                        Collections.singleton(Integer.toString(fileSwid)));
                    return server.get().metadata().getAnalysisProvenance(query).stream();
                  })
              .flatMap(ap -> ap.getIusLimsKeys().stream())
              .map(IusLimsKey::getLimsKey)
              .sorted(LIMS_KEY_COMPARATOR)
              .distinct()
              .map(
                  key ->
                      Integer.toString(
                          server
                              .get()
                              .metadata()
                              .addIUS(
                                  server
                                      .get()
                                      .metadata()
                                      .addLimsKey(
                                          key.getProvider(),
                                          key.getId(),
                                          key.getVersion(),
                                          key.getLastModified()),
                                  false)))
              .collect(Collectors.toList());

      final File iniFile = File.createTempFile("niassa", ".ini");
      iniFile.deleteOnExit();
      try (OutputStream out = new FileOutputStream(iniFile)) {
        ini.store(out, String.format("Generated by Shesmu for workflow %d", workflowAccession));
      }

      boolean success;
      try {
        final Scheduler scheduler =
            new Scheduler(
                server.get().metadata(),
                server
                    .get()
                    .settings()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Object::toString, Object::toString)));
        runAccession =
            scheduler
                .scheduleInstalledBundle(
                    Long.toString(workflowAccession),
                    Collections.singletonList(iniFile.getAbsolutePath()),
                    true,
                    parentSwids.stream().map(Object::toString).collect(Collectors.toList()),
                    Stream.concat(iusAccessions.stream(), fileLimsKeyAccessions.stream())
                        .collect(Collectors.toList()),
                    Collections.emptyList(),
                    server.get().host(),
                    server
                        .get()
                        .settings()
                        .getProperty(
                            SqwKeys.SW_DEFAULT_WORKFLOW_ENGINE.getSettingKey(),
                            Engines.DEFAULT_ENGINE),
                    fileSwids)
                .getReturnValue();
        server.get().analysisCache().invalidate(workflowAccession);
        WorkflowRunAttribute attribute = new WorkflowRunAttribute();
        attribute.setTag("major_olive_version");
        attribute.setValue(Long.toString(majorOliveVersion));
        server.get().metadata().annotateWorkflowRun(runAccession, attribute, null);
        success = runAccession != 0;
      } catch (Exception e) {
        // Suppress all the batshit crazy errors this thing can throw
        e.printStackTrace();
        success = false;
      }

      // Indicate if we managed to schedule the workflow; if we did, mark ourselves
      // dirty so there is a delay before our next query.
      (success ? runCreated : runFailed)
          .labels(server.get().url(), Long.toString(workflowAccession))
          .inc();
      hasLaunched = true;
      return success ? ActionState.QUEUED : ActionState.FAILED;
    } catch (final Exception e) {
      e.printStackTrace();
      return ActionState.FAILED;
    }
  }

  @Override
  public final void prepare() {
    fileAccessions =
        fileSwids.stream().sorted().map(Object::toString).collect(Collectors.joining(","));
  }

  @Override
  public final int priority() {
    return 0;
  }

  @Override
  public void purgeCleanup() {
    if (first) MAX_IN_FLIGHT.get(workflowAccession).first().incrementAndGet();
    if (inflight) MAX_IN_FLIGHT.get(workflowAccession).second().incrementAndGet();
  }

  @Override
  public final long retryMinutes() {
    return 10;
  }

  @Override
  public boolean search(Pattern query) {
    return ini.values().stream().anyMatch(v -> query.matcher(v.toString()).matches());
  }

  @Override
  public final ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("majorOliveVersion", majorOliveVersion);
    limsKeys
        .stream()
        .map(
            k -> {
              final ObjectNode key = mapper.createObjectNode();
              key.put("provider", k.getProvider());
              key.put("id", k.getId());
              key.put("version", k.getVersion());
              key.put("lastModified", DateTimeFormatter.ISO_INSTANT.format(k.getLastModified()));
              return key;
            })
        .forEach(node.putArray("limsKeys")::add);
    final ObjectNode iniJson = node.putObject("ini");
    ini.forEach((key, value) -> iniJson.put(key.toString(), value.toString()));
    node.put("workflowAccession", workflowAccession);
    if (runAccession != 0) {
      node.put("workflowRunAccession", runAccession);
    }

    fileSwids.forEach(node.putArray("fileAccessions")::add);
    parentSwids.forEach(node.putArray("parentAccessions")::add);
    final ObjectNode iniNode = node.putObject("ini");
    ini.forEach((k, v) -> iniNode.put(k.toString(), v.toString()));

    return node;
  }

  private LongStream workflowAccessions() {
    return LongStream.concat(LongStream.of(workflowAccession), LongStream.of(previousAccessions));
  }
}
