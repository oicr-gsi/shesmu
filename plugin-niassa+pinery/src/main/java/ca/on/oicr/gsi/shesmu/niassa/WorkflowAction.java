package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import io.seqware.Engines;
import io.seqware.pipeline.SqwKeys;
import io.seqware.pipeline.api.Scheduler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.sourceforge.seqware.common.model.IUSAttribute;
import net.sourceforge.seqware.common.model.WorkflowRun;
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

  static final Comparator<LimsKey> LIMS_ID_COMPARATOR =
      Comparator.comparing(LimsKey::getProvider).thenComparing(LimsKey::getId);

  static final Comparator<LimsKey> LIMS_KEY_COMPARATOR =
      LIMS_ID_COMPARATOR.thenComparing(LimsKey::getVersion);
  public static final String MAJOR_OLIVE_VERSION = "major_olive_version";
  private static final LatencyHistogram launchTime =
      new LatencyHistogram(
          "shesmu_niassa_wr_launch_time", "The time to launch a workflow run.", "workflow");
  private static final LatencyHistogram matchTime =
      new LatencyHistogram(
          "shesmu_niassa_wr_match_time",
          "The time to match an action against the workflow runs in the database.",
          "workflow");
  private static final Gauge runCreated =
      Gauge.build("shesmu_niassa_run_created", "The number of workflow runs launched.")
          .labelNames("target", "workflow")
          .register();
  private static final Gauge runFailed =
      Gauge.build(
              "shesmu_niassa_run_failed", "The number of workflow runs that failed to be launched.")
          .labelNames("target", "workflow")
          .register();
  private static final LatencyHistogram updateTime =
      new LatencyHistogram(
          "shesmu_niassa_wr_update_time",
          "The time to pull the status of a workflow run.",
          "workflow");
  private final Map<String, String> annotations;
  private boolean cacheCollision;
  private List<String> errors = Collections.emptyList();
  private Optional<Instant> externalTimestamp = Optional.empty();
  private final FileMatchingPolicy fileMatchingPolicy;
  private boolean hasLaunched;
  private boolean ignoreMaxInFlight;
  Properties ini = new Properties();
  private ActionState lastState = ActionState.UNKNOWN;
  private InputLimsCollection limsKeysCollection;
  private long majorOliveVersion;
  private List<WorkflowRunMatch> matches = Collections.emptyList();
  private final long[] previousAccessions;

  @ActionParameter(required = false)
  public long priority;

  private boolean priorityBoost;
  private int runAccession;
  private final Supplier<NiassaServer> server;
  private final List<String> services;
  private final long workflowAccession;
  private final String workflowName;

  public WorkflowAction(
      Supplier<NiassaServer> server,
      String workflowName,
      long workflowAccession,
      long[] previousAccessions,
      FileMatchingPolicy fileMatchingPolicy,
      List<String> services,
      Map<String, String> annotations) {
    super("niassa");
    this.server = server;
    this.workflowName = workflowName;
    this.workflowAccession = workflowAccession;
    this.previousAccessions = previousAccessions;
    this.fileMatchingPolicy = fileMatchingPolicy;
    this.services = services;
    this.annotations = new TreeMap<>(annotations);
    // This sort will group workflows together by SWID so that as many as possible will performed
    // sequentially before analysis cache expires and needs reloading.

    priority = (workflowAccession % 10);
  }

  @Override
  public Stream<Pair<String, String>> commands() {
    return Stream.concat(
        Stream.of(
            ignoreMaxInFlight
                ? new Pair<>("🚲 Respect Max-in-flight Limit", "NIASSA-RESPECT-MAX-IN-FLIGHT")
                : new Pair<>("✈️ Ignore Max-in-flight Limit", "NIASSA-IGNORE-MAX-IN-FLIGHT"),
            priorityBoost
                ? new Pair<>("🚀 Use Normal Priority", "NIASSA-PRIORITY-NICE")
                : new Pair<>("🚀 Use High Priority", "NIASSA-PRIORITY-BOOST")),
        runAccession == 0
            ? (matches.isEmpty()
                ? Stream.empty()
                : Stream.of(
                    new Pair<>(
                        "🚧 Skip All Partially Matched Workflow Runs", "NIASSA-SKIP-CANDIDATES")))
            : Stream.of(
                new Pair<>("💔 Reset Workflow Run Connection", "NIASSA-RESET-WFR"),
                new Pair<>("🚧 Skip and Re-run", "NIASSA-SKIP-RERUN")));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WorkflowAction that = (WorkflowAction) o;
    return majorOliveVersion == that.majorOliveVersion
        && workflowAccession == that.workflowAccession
        && Objects.equals(annotations, that.annotations)
        && Objects.equals(limsKeysCollection, that.limsKeysCollection);
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return externalTimestamp;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(Utils.toBytes(majorOliveVersion));
    digest.accept(Utils.toBytes(workflowAccession));
    for (final Map.Entry<String, String> annotation : annotations.entrySet()) {
      digest.accept(annotation.getKey().getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {'='});
      digest.accept(annotation.getValue().getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
    }
    limsKeysCollection.generateUUID(digest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(majorOliveVersion, workflowAccession, annotations, limsKeysCollection);
  }

  void limsKeyCollection(InputLimsCollection limsKeys) {
    this.limsKeysCollection = limsKeys;
  }

  @ActionParameter(name = "major_olive_version")
  public final void majorOliveVersion(long majorOliveVersion) {
    this.majorOliveVersion = majorOliveVersion;
  }

  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  public final ActionState perform(ActionServices actionServices) {
    cacheCollision = false;
    if (actionServices.isOverloaded(
        Stream.concat(services.stream(), server.get().services()).collect(Collectors.toSet()))) {
      return ActionState.THROTTLED;
    }
    final List<String> errors = new ArrayList<>();
    if (limsKeysCollection.shouldHalp(errors::add)) {
      this.errors = errors;
      return ActionState.HALP;
    }
    try {
      // If we know our workflow run, check its status
      if (runAccession != 0) {
        try (AutoCloseable timer = updateTime.start(Long.toString(workflowAccession))) {
          final WorkflowRun run = server.get().metadata().getWorkflowRun(runAccession);

          final ActionState state = NiassaServer.processingStateToActionState(run.getStatus());
          if (state != lastState) {
            lastState = state;
            externalTimestamp = Optional.of(run.getUpdateTimestamp().toInstant());
            // When we are getting the analysis state, we are bypassing the analysis cache, so we
            // may see a state transition before any of our sibling workflow runs. If we see that
            // happen, zap the cache so they will see it. We may have selected a previous workflow
            // run, so zap the right cache.
            server.get().invalidateMaxInFlight(run.getWorkflowAccession().longValue());
          }
          return state;
        }
      }
      // Read the analysis provenance cache to determine if this workflow has already been run
      final Set<Integer> inputFileSWIDs =
          limsKeysCollection.fileSwids().collect(Collectors.toSet());
      final List<SimpleLimsKey> limsKeys =
          limsKeysCollection
              .limsKeys()
              .map(SimpleLimsKey::new)
              .sorted(LIMS_KEY_COMPARATOR)
              .distinct()
              .collect(Collectors.toList());
      final Map<SimpleLimsKey, Set<String>> signatures =
          limsKeysCollection
              .signatures()
              .collect(
                  Collectors.groupingBy(
                      p -> new SimpleLimsKey(p.first()),
                      Collectors.mapping(Pair::second, Collectors.toSet())));
      try (AutoCloseable timer = matchTime.start(Long.toString(workflowAccession))) {
        matches =
            workflowAccessions()
                .distinct()
                .boxed()
                .flatMap(
                    accession ->
                        server
                            .get()
                            .analysisCache()
                            .get(accession)
                            .map(
                                as ->
                                    as.compare(
                                        workflowAccessions(),
                                        Long.toString(majorOliveVersion),
                                        fileMatchingPolicy,
                                        inputFileSWIDs,
                                        limsKeys,
                                        signatures,
                                        annotations)))
                .filter(pair -> pair.comparison() != AnalysisComparison.DIFFERENT)
                .sorted()
                .collect(Collectors.toList());
      } catch (InitialCachePopulationException e) {
        this.errors =
            Collections.singletonList(
                "Failed to pull previous workflow runs from Niassa. This can either be a temporary error if another action is fetching that data or a permanent one caused by an incorrect workflow SWID.");
        cacheCollision = true;
        return ActionState.FAILED;
      }
      if (!matches.isEmpty()) {
        // We found a matching workflow run in analysis provenance; the least stale, most complete,
        // newest workflow is selected; if that workflow run is stale, we know there are no better
        // candidates and we should ignore the workflow run's state and complain.
        final WorkflowRunMatch match = matches.get(0);
        if (match.comparison() == AnalysisComparison.EXACT
            || match.comparison() == AnalysisComparison.FIXABLE) {
          // Don't associate with this workflow because we don't want to get ourselves to this state
          runAccession = match.state().workflowRunAccession();
          // We matched, but we might need to update the LIMS keys OR add missing signatures (if the
          // LIMS keys match exactly but signatures are absent)
          if (match.comparison() == AnalysisComparison.FIXABLE) {
            match.fixVersions(match.state().workflowAccession(), server.get().metadata());
          } else {
            match.fixSignatures(match.state().workflowAccession(), server.get().metadata());
          }
        }
        externalTimestamp = Optional.ofNullable(match.state().lastModified());
        this.errors = Collections.emptyList();
        return match.actionState();
      }

      // We get exactly one attempt to launch a job. If we fail, that's the end of this action. A
      // human needs to rescue us.
      if (hasLaunched) {
        this.errors =
            Collections.singletonList("Workflow has already been attempted. Purge to try again.");
        return ActionState.FAILED;
      }
      try {
        // Check if there are already too many copies of this workflow running; if so, wait until
        // later.
        if (!ignoreMaxInFlight && server.get().maxInFlight(workflowName, workflowAccession)) {
          this.errors =
              Collections.singletonList(
                  "Too many workflows running. Sit tight or increase max-in-flight setting.");
          return ActionState.WAITING;
        }
      } catch (InitialCachePopulationException e) {
        this.errors =
            Collections.singletonList(
                "Failed to pull active workflow run count from Niassa. This can either be a temporary error if another action is fetching that data or a permanent one caused by an incorrect workflow SWID.");
        cacheCollision = true;
        return ActionState.FAILED;
      }

      // Tell the input LIMS collection to register all the LIMS keys and prepare the INI file as
      // appropriate. We provide a callback to do the registration, keep track of all registered IUS
      // accessions to automatically associate them with the workflow run, deduplicate them.
      final Map<SimpleLimsKey, Integer> iusAccessions = new HashMap<>();
      limsKeysCollection.prepare(
          key ->
              iusAccessions.computeIfAbsent(
                  new SimpleLimsKey(key),
                  k ->
                      server
                          .get()
                          .metadata()
                          .addIUS(
                              server
                                  .get()
                                  .metadata()
                                  .addLimsKey(
                                      k.getProvider(),
                                      k.getId(),
                                      k.getVersion(),
                                      k.getLastModified()),
                              false)),
          ini);
      for (final Map.Entry<? extends LimsKey, Set<String>> signature : signatures.entrySet()) {
        final int iusAccession = iusAccessions.get(new SimpleLimsKey(signature.getKey()));
        server
            .get()
            .metadata()
            .annotateIUS(
                iusAccession,
                signature
                    .getValue()
                    .stream()
                    .map(
                        s -> {
                          final IUSAttribute attribute = new IUSAttribute();
                          attribute.setTag("signature");
                          attribute.setValue(s);
                          return attribute;
                        })
                    .collect(Collectors.toSet()));
      }

      final File iniFile = File.createTempFile("niassa", ".ini");
      iniFile.deleteOnExit();
      try (OutputStream out = new FileOutputStream(iniFile)) {
        ini.store(out, String.format("Generated by Shesmu for workflow %d", workflowAccession));
      }

      boolean success;
      try (AutoCloseable timer = launchTime.start(Long.toString(workflowAccession))) {
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
                    Collections.emptyList(),
                    iusAccessions
                        .values()
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()),
                    Collections.emptyList(),
                    server.get().host(),
                    server
                        .get()
                        .settings()
                        .getProperty(
                            SqwKeys.SW_DEFAULT_WORKFLOW_ENGINE.getSettingKey(),
                            Engines.DEFAULT_ENGINE),
                    limsKeysCollection.fileSwids().collect(Collectors.toSet()))
                .getReturnValue();
        hasLaunched = true;
        // Zap the cache so any other workflows will see this workflow running and won't exceed our
        // budget
        server.get().invalidateMaxInFlight(workflowAccession);
        final WorkflowRunAttribute attribute = new WorkflowRunAttribute();
        attribute.setTag(MAJOR_OLIVE_VERSION);
        attribute.setValue(Long.toString(majorOliveVersion));
        server.get().metadata().annotateWorkflowRun(runAccession, attribute, null);
        for (final Map.Entry<String, String> annotation : annotations.entrySet()) {
          WorkflowRunAttribute userAttribute = new WorkflowRunAttribute();
          userAttribute.setTag(annotation.getKey());
          userAttribute.setValue(annotation.getValue());
          server.get().metadata().annotateWorkflowRun(runAccession, userAttribute, null);
        }
        success = runAccession != 0;
        externalTimestamp = Optional.of(Instant.now());
      } catch (Exception e) {
        // Suppress all the batshit crazy errors this thing can throw
        e.printStackTrace();
        this.errors = Collections.singletonList(e.getMessage());
        success = false;
      }

      // Indicate if we managed to schedule the workflow; if we did, mark ourselves
      // dirty so there is a delay before our next query.
      (success ? runCreated : runFailed)
          .labels(server.get().url(), Long.toString(workflowAccession))
          .inc();
      lastState = success ? ActionState.QUEUED : ActionState.FAILED;
      this.errors = Collections.emptyList();
      return lastState;
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
  }

  @Override
  public synchronized boolean performCommand(String commandName) {
    switch (commandName) {
      case "NIASSA-RESPECT-MAX-IN-FLIGHT":
        if (ignoreMaxInFlight) {
          ignoreMaxInFlight = false;
          return true;
        }
        return false;
      case "NIASSA-IGNORE-MAX-IN-FLIGHT":
        if (!ignoreMaxInFlight) {
          ignoreMaxInFlight = true;
          return true;
        }
        return false;
      case "NIASSA-PRIORITY-BOOST":
        if (!priorityBoost) {
          priorityBoost = true;
          return true;
        }
        return false;
      case "NIASSA-PRIORITY-NICE":
        if (priorityBoost) {
          priorityBoost = false;
          return true;
        }
        return false;
      case "NIASSA-SKIP-CANDIDATES":
        if (matches.isEmpty() || runAccession != 0) {
          return false;
        }
        for (final WorkflowRunMatch match : matches) {
          final WorkflowRunAttribute attribute = new WorkflowRunAttribute();
          attribute.setTag("skip");
          attribute.setValue("shesmu-ui");
          server
              .get()
              .metadata()
              .annotateWorkflowRun(match.state().workflowRunAccession(), attribute, null);
        }
        return true;
      case "NIASSA-SKIP-RERUN":
        if (runAccession == 0) {
          return false;
        } else {
          final WorkflowRunAttribute attribute = new WorkflowRunAttribute();
          attribute.setTag("skip");
          attribute.setValue("shesmu-ui");
          server.get().metadata().annotateWorkflowRun(runAccession, attribute, null);
        }
        // Intentional fall through
      case "NIASSA-RESET-WFR":
        if (runAccession == 0) {
          return false;
        } else {
          runAccession = 0;
          hasLaunched = false;
          lastState = ActionState.UNKNOWN;
          server.get().analysisCache().invalidate(workflowAccession);
          return true;
        }
    }
    return false;
  }

  @Override
  public final int priority() {
    return (int) ((priorityBoost ? 10 : 1) * priority);
  }

  @Override
  public void purgeCleanup() {
    // If this action is being purged, there's a good chance the user is trying to skip & rerun, so
    // zap the cache so we see that change.
    server.get().analysisCache().invalidate(workflowAccession);
  }

  @Override
  public final long retryMinutes() {
    // If there was an error populating the cache, then it's likely that the cache was being
    // populated by another action and it will be ready for us to consume very soon.
    return cacheCollision ? 2 : 10;
  }

  @Override
  public boolean search(Pattern query) {
    return ini.values().stream().anyMatch(v -> query.matcher(v.toString()).matches())
        || (runAccession != 0 && query.matcher(Integer.toString(runAccession)).matches())
        || query.matcher(workflowName).matches()
        || limsKeysCollection.matches(query)
        || workflowAccessions()
            .anyMatch(workflow -> query.matcher(Long.toString(workflow)).matches())
        || matches
            .stream()
            .anyMatch(
                match ->
                    query
                        .matcher(Integer.toString(match.state().workflowRunAccession()))
                        .matches());
  }

  void setAnnotation(String tag, String value) {
    annotations.putIfAbsent(tag, value);
  }

  @Override
  public final ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("majorOliveVersion", majorOliveVersion);
    limsKeysCollection
        .limsKeys()
        .sorted(LIMS_KEY_COMPARATOR)
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
    limsKeysCollection
        .signatures()
        .map(
            signature -> {
              final ObjectNode obj = mapper.createObjectNode();
              obj.put("provider", signature.first().getProvider());
              obj.put("id", signature.first().getId());
              obj.put("version", signature.first().getVersion());
              obj.put(
                  "lastModified",
                  DateTimeFormatter.ISO_INSTANT.format(signature.first().getLastModified()));
              obj.put("signature", signature.second());
              return obj;
            })
        .forEach(node.putArray("signatures")::add);
    final ObjectNode iniJson = node.putObject("ini");
    ini.forEach((key, value) -> iniJson.put(key.toString(), value.toString()));
    node.put("workflowAccession", workflowAccession);
    node.put("workflowName", workflowName);
    node.put("cromwellUrl", server.get().cromwellUrl().orElse(null));
    if (runAccession != 0) {
      final WorkflowRunEssentials essentials = server.get().directoryAndIni(runAccession);
      node.put("workflowRunAccession", runAccession);
      node.put("workingDirectory", essentials.currentDirectory());
      node.put("cromwellId", essentials.cromwellId());
      node.put("cromwellRoot", essentials.cromwellRoot());
      final ObjectNode discoveredIniNode = node.putObject("discoveredIni");
      essentials.ini().forEach((k, v) -> discoveredIniNode.put(k.toString(), v.toString()));
      final ArrayNode cromwellLogs = node.putArray("cromwellLogs");
      essentials
          .cromwellLogs()
          .forEach(
              (task, logs) ->
                  logs.forEach(
                      log -> {
                        final ObjectNode logEntry = cromwellLogs.addObject();
                        logEntry.put("task", task);
                        logEntry.put("attempt", log.getAttempt());
                        logEntry.put("shardIndex", log.getShardIndex());
                        logEntry.put("stderr", log.getStderr());
                        logEntry.put("stdout", log.getStdout());
                      }));
    }
    final ObjectNode iniNode = node.putObject("ini");
    ini.forEach((k, v) -> iniNode.put(k.toString(), v.toString()));
    final ArrayNode matches = node.putArray("matches");
    this.matches.forEach(match -> matches.add(match.toJson(mapper)));
    this.annotations.forEach(node.putObject("annotations")::put);
    this.errors.forEach(node.putArray("errors")::add);

    return node;
  }

  private LongStream workflowAccessions() {
    return LongStream.concat(LongStream.of(workflowAccession), LongStream.of(previousAccessions));
  }
}
