package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.vidarr.RunState;
import ca.on.oicr.gsi.shesmu.vidarr.RunStateAttemptSubmit;
import ca.on.oicr.gsi.vidarr.api.ExternalKey;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.model.Workflow;

public final class MigrationAction extends Action {

  static final Comparator<LimsKey> LIMS_ID_COMPARATOR =
      Comparator.comparing(LimsKey::getProvider).thenComparing(LimsKey::getId);
  static final Comparator<LimsKey> LIMS_KEY_COMPARATOR =
      LIMS_ID_COMPARATOR.thenComparing(LimsKey::getVersion);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final LatencyHistogram matchTime =
      new LatencyHistogram(
          "shesmu_niassa_migrate_match_time",
          "The time to match an action against the workflow runs in the database.",
          "workflow");

  private static Pair<String, String> extractNewProvider(String legacyProvider) {
    if (legacyProvider.equals("pinery-miso") || legacyProvider.equals("pinery-miso-2.2")) {
      return new Pair<>("pinery-miso", "1");
    } else {
      int index = legacyProvider.indexOf("-v");
      return new Pair<>(legacyProvider.substring(0, index), legacyProvider.substring(index + 2));
    }
  }

  private final Map<String, String> annotations;
  private boolean cacheCollision;
  private List<String> errors = Collections.emptyList();
  private final FileMatchingPolicy fileMatchingPolicy;
  Properties ini = new Properties();
  private InputLimsCollection limsKeysCollection;
  private long majorOliveVersion;
  private List<WorkflowRunMatch> matches = Collections.emptyList();
  private final long[] previousAccessions;

  @ActionParameter(required = false)
  public long priority;

  private boolean priorityBoost;
  private final boolean relaunchFailedOnUpgrade;
  final SubmitWorkflowRequest request = new SubmitWorkflowRequest();
  private final Definer<NiassaServer> server;
  private final Set<String> services;
  private boolean stale;
  private RunState state = new RunStateAttemptSubmit();
  private final Set<String> userLabels;
  private final long workflowAccession;
  private final String workflowName;

  public MigrationAction(
      Definer<NiassaServer> server,
      String workflowName,
      long workflowAccession,
      long[] previousAccessions,
      FileMatchingPolicy fileMatchingPolicy,
      List<String> services,
      Map<String, String> annotations,
      boolean relaunchFailedOnUpgrade,
      Set<String> userLabels) {
    super("niassa-migration");
    this.server = server;
    this.workflowName = workflowName;
    this.workflowAccession = workflowAccession;
    this.previousAccessions = previousAccessions;
    this.fileMatchingPolicy = fileMatchingPolicy;
    this.services = new TreeSet<>(services);
    this.annotations = new TreeMap<>(annotations);
    // This sort will group workflows together by SWID so that as many as possible will performed
    // sequentially before analysis cache expires and needs reloading.

    priority = (workflowAccession % 10);
    this.relaunchFailedOnUpgrade = relaunchFailedOnUpgrade;
    this.userLabels = userLabels;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MigrationAction that = (MigrationAction) o;

    return workflowAccession == that.workflowAccession
        && Objects.equals(annotations, that.annotations)
        && Objects.equals(ini, that.ini)
        && Objects.equals(limsKeysCollection, that.limsKeysCollection);
  }

  // todo: this will have the same ids as the real actions. Is this problematic??
  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(Utils.toBytes(workflowAccession));
    generateUUID(digest, annotations, s -> s.getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    generateUUID(digest, ini, v -> v.toString().getBytes(StandardCharsets.UTF_8));

    limsKeysCollection.generateUUID(digest);
  }

  private <T> void generateUUID(
      Consumer<byte[]> digest, Map<T, T> map, Function<T, byte[]> transform) {
    for (final Map.Entry<T, T> entry : map.entrySet()) {
      digest.accept(transform.apply(entry.getKey()));
      digest.accept(new byte[] {'='});
      digest.accept(transform.apply(entry.getValue()));
      digest.accept(new byte[] {0});
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(workflowAccession, annotations, limsKeysCollection);
  }

  void limsKeyCollection(InputLimsCollection limsKeys) {
    this.limsKeysCollection = limsKeys;
  }

  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  public synchronized ActionState perform(ActionServices actionServices) {
    final var errors = new ArrayList<String>();
    if (limsKeysCollection.shouldZombie(errors::add)) {
      this.errors = errors;
      return ActionState.ZOMBIE;
    }

    // Read the analysis provenance cache to determine if this workflow has already been run
    final Set<Integer> inputFileSWIDs = limsKeysCollection.fileSwids().collect(Collectors.toSet());
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
              .filter(
                  pair ->
                      pair.comparison() != AnalysisComparison.DIFFERENT
                          && pair.actionState() == ActionState.SUCCEEDED)
              .sorted()
              .collect(Collectors.toList());
    } catch (InitialCachePopulationException e) {
      final Metadata metadata = server.get().metadata();
      final Workflow workflow = metadata.getWorkflow((int) workflowAccession);
      metadata.clean_up();
      if (workflow == null) {
        this.errors =
            Collections.singletonList("Niassa doesn't seem to recognise this workflow SWID.");
        cacheCollision = false;
        return ActionState.FAILED;
      }
      this.errors =
          Collections.singletonList(
              "Another workflow is fetching the previous workflow runs from Niassa.");
      cacheCollision = true;
      return ActionState.WAITING;
    } catch (Exception e) {
      e.printStackTrace();
      this.errors = List.of(e.getMessage());
      return ActionState.FAILED;
    }

    if (matches.isEmpty()) {
      // go into waiting state and try again later
      this.errors = Collections.singletonList("No candidate workflow runs for migration.");
      return ActionState.WAITING;
    } else {
      final var labels = MAPPER.createObjectNode();
      final var arguments = MAPPER.createObjectNode();
      final var metadata = MAPPER.createObjectNode();
      annotations.entrySet().stream()
          .filter(e -> userLabels.contains(e.getKey()))
          .forEach(e -> labels.put(e.getKey(), e.getValue()));

      // Populate external keys
      Set<ExternalKey> externalKeys = new HashSet<>();
      limsKeys.forEach(
          limsKey -> {
            final var newProvider = extractNewProvider(limsKey.getProvider());

            ExternalKey key =
                new ExternalKey(
                    newProvider.first(),
                    limsKey.getId(),
                    Map.of("pinery-hash-" + newProvider.second(), limsKey.getVersion()));
            externalKeys.add(key);
          });

      // Populate arguments
      final var vidarrDBUrl = server.get().vidarrDbUrl().orElse(null);
      final var user = server.get().vidarrDbUser().orElse(null);
      final var password = server.get().vidarrDbPassword().orElse(null);

      // Get hash id's for all input file SWIDs from matches and populate metadata
      if (vidarrDBUrl == null) {
        this.errors = List.of("Vidarr not configured");
        return ActionState.HALP;
      }
      try (Connection conn = DriverManager.getConnection(vidarrDBUrl, user, password)) {
        Statement statement = conn.createStatement();
        final var inputFiles = matches.get(0).state().inputFiles();
        final var workflowRunSWID = matches.get(0).state().workflowRunAccession();
        final var workflowRunEssentials = server.get().directoryAndIni(workflowRunSWID);

        ResultSet rs =
            statement.executeQuery(
                "SELECT hash_id FROM analysis WHERE (labels->>'niassa-file-accession')::integer "
                    + "IN ("
                    + inputFiles.stream().map(String::valueOf).collect(Collectors.joining(","))
                    + ")");

        final var arrayNode = MAPPER.createArrayNode();
        var count = 0;
        while (rs.next()) {
          String hashId = rs.getString("hash_id");
          var node = MAPPER.createObjectNode();
          node.put("type", "INTERNAL");
          node.putArray("contents").add("vidarr:_/file/" + hashId);
          arrayNode.add(node);
          count++;
        }
        if (count != inputFiles.size()) {
          this.errors = List.of("Input files have not been converted");
          return ActionState.WAITING;
        }

        arguments.put("workflowRunSWID", workflowRunSWID);
        arguments.putPOJO("inputFiles", arrayNode);
        final var ini = arguments.putObject("ini");
        for (final var entry : workflowRunEssentials.ini().entrySet()) {
          ini.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
      } catch (SQLException e) {
        e.printStackTrace();
        this.errors = List.of(e.getMessage());
        return ActionState.FAILED; // Should this fail?
      }

      // For each output file, create following object entry:
      // { "fileSWID": 12346, "output": {"type": "MANUAL", "contents": [
      // "/scratch2/group/gsi/development/vidarr", [{ "provider": "pinery-miso", "id":
      // "123_1_LDI100" }]]}}
      final var outputFileMigration = metadata.putArray("migration");
      final var outputFiles = matches.get(0).state().files();
      outputFiles.forEach(
          file -> {
            final var fileNode = outputFileMigration.addObject();
            final var outputNode = fileNode.putObject("fileMetadata");
            outputNode.put("type", "MANUAL");
            final var contents = outputNode.putArray("contents");
            contents
                .addObject()
                .put("outputDirectory", server.get().outputDirectory().orElseThrow());
            var limsKeyNode = contents.addArray();
            file.iterator()
                .forEachRemaining(
                    limsKey -> {
                      var content = MAPPER.createObjectNode();
                      content.put("provider", extractNewProvider(limsKey.first()).first());
                      content.put("id", limsKey.second());
                      limsKeyNode.add(content);
                    });
            fileNode.put("fileSWID", file.getAccession());
          });

      request.setArguments(arguments);
      request.setConsumableResources(new TreeMap<>());
      request.setExternalKeys(externalKeys);
      request.setLabels(labels);
      request.setMetadata(metadata);
      request.setTarget("MIGRATION");
      request.setWorkflow(workflowName);
      request.setWorkflowVersion(
          matches.get(0).state().workflowVersion()
              + "."
              + matches.get(0).state().workflowAccession());

      if (stale) {
        return ActionState.ZOMBIE;
      }

      final RunState.PerformResult result =
          server
              .get()
              .vidarrUrl()
              .map(
                  url -> {
                    try {
                      return state.perform(url, request);
                    } catch (IOException | InterruptedException e) {
                      e.printStackTrace();
                      return new RunState.PerformResult(
                          List.of(e.getMessage()), ActionState.UNKNOWN, state);
                    }
                  })
              .orElseGet(
                  () ->
                      new RunState.PerformResult(
                          List.of("Internal error: No Vidarr URL available"),
                          ActionState.UNKNOWN,
                          state));
      this.errors = result.errors();
      state = result.nextState();
      return result.actionState();
    }
  }

  @Override
  public final int priority() {
    return (int) ((priorityBoost ? 10 : 1) * priority);
  }

  @Override
  public final long retryMinutes() {
    // If there was an error populating the cache, then it's likely that the cache was being
    // populated by another action and it will be ready for us to consume very soon.
    return cacheCollision ? 2 : 10;
  }

  @Override
  public boolean search(Pattern query) {
    return false;
  }

  void setAnnotation(String tag, String value) {
    annotations.putIfAbsent(tag, value.length() < 256 ? value : value.substring(0, 255));
  }

  @Override
  public final ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.putPOJO("request", request);
    node.put("priority", priority);
    services.forEach(node.putArray("services")::add);
    errors.forEach(node.putArray("errors")::add);
    state.writeJson(mapper, node);
    return node;
  }

  private LongStream workflowAccessions() {
    return LongStream.concat(LongStream.of(workflowAccession), LongStream.of(previousAccessions));
  }
}
