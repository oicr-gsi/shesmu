package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonListBodyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ArchiveCaseAction extends JsonParameterisedAction {

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Counter NabuRequestErrors =
      Counter.build(
              "shesmu_nabu_request_errors",
              "The number of errors trying to countact the Nabu web service.")
          .labelNames("target")
          .register();
  private static final LatencyHistogram NabuRequestTime =
      new LatencyHistogram(
          "shesmu_nabu_request_time",
          "The request time latency to launch a remote action.",
          "target");

  private List<String> errors = List.of();
  final Definer<NabuPlugin> owner;
  private Optional<Instant> externalTimestamp = Optional.empty();
  private int priority;
  private String caseID;
  private final ObjectNode parameters;
  private final ObjectNode rootParameters = MAPPER.createObjectNode();
  private boolean stale;

  public ArchiveCaseAction(
      Definer<NabuPlugin> owner, String targetName, String workflowName, String workflowVersion) {
    super("nabu-run");
    this.owner = owner;
    parameters = rootParameters.putObject("parameters");
  }

  public ObjectNode parameters() {
    //    final var request =
    //            String.format(
    //                    "{ \"caseIdentifier\":%s, \"requisitionId\":%s,  \"limsIds\":%s,
    // \"workflowRunIdsForOffsiteArchive\":[%s],"
    //                            + " \"workflowRunIdsForVidarrArchival\":[%s]}",
    //                    cArchive.getCaseIdentifier(),
    //                    cArchive.getRequisitionId(),
    //                    cArchive.getLimsIds(),
    //                    wfrIdsForOffsiteArchiveString,
    //                    wfrIdsForVidarrArchivalString);
    return parameters;
  }

  private ActionState actionStatusFromArchive(NabuCaseArchiveDto caseArchive) {
    if (caseArchive.getCreated() != null) {
      if (caseArchive.getCommvaultBackupJobId() != null
          && caseArchive.getFilesCopiedToOffsiteArchiveStagingDir() != null
          && caseArchive.getFilesLoadedIntoVidarrArchival() != null) {
        return ActionState.SUCCEEDED;
      } else {
        return ActionState.WAITING;
      }
    }
    return ActionState.UNKNOWN;
  }

  public ActionState perform(ActionServices services, String baseUrl, NabuCaseArchiveDto cArchive)
      throws IOException {

    final var overloaded = services.isOverloaded("all", "nabu");
    if (!overloaded.isEmpty()) {
      errors = Collections.singletonList("Overloaded services: " + String.join(", ", overloaded));
      return ActionState.THROTTLED;
    }

    BodyPublisher body;
    try {
      body = BodyPublishers.ofString(MAPPER.writeValueAsString(rootParameters));
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }

    final var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/case"));
    final var wfrIdsForOffsiteArchiveString =
        cArchive.getWorkflowRunIdsForOffsiteArchive().stream()
            .collect(Collectors.joining("','", "'", "'"));
    final var wfrIdsForVidarrArchivalString =
        cArchive.getWorkflowRunIdsForVidarrArchival().stream()
            .collect(Collectors.joining("','", "'", "'"));
    final var createRequest = builder.header("Content-Type", "application/json").POST(body).build();
    final var request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/case"))
            .header("Content-type", "application/json")
            .header("Accept", "application/json")
            .POST(body)
            .build();
    try (var timer = NabuRequestTime.start(owner.get().NabuUrl())) {
      var response =
          HTTP_CLIENT.send(request, new JsonListBodyHandler<>(MAPPER, NabuCaseArchiveDto.class));
      if (response.statusCode() / 100 != 2) {
        showError(response, request.uri());
        NabuRequestErrors.labels(owner.get().NabuUrl()).inc();
        return ActionState.FAILED;
      }
      final var results = response.body().get().collect(Collectors.toList());
      //        final var archive =
      //
      // Stream.of(results).max(Comparator.comparing(NabuCaseArchiveDto::getGenerated)).get();
      //        externalTimestamp =
      // Optional.of(ZonedDateTime.parse(archive.getCreated()).toInstant());
      return actionStatusFromArchive(results.get(0));

    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      NabuRequestErrors.labels(owner.get().NabuUrl()).inc();
      return ActionState.FAILED;
    }
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
    final var other = (ArchiveCaseAction) obj;
    return false;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(caseID.getBytes());
    try {
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    ;
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + (owner == null ? 0 : owner.hashCode());
    result = prime * result + (parameters == null ? 0 : parameters.hashCode());
    result = prime * result + (caseID == null ? 0 : caseID.hashCode());
    return result;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    return ActionState.FAILED;
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 1;
  }

  private void showError(HttpResponse<?> response, URI url)
      throws UnsupportedOperationException, IOException {
    final List<String> errors = new ArrayList<>();
    final Map<String, String> labels = new TreeMap<>();
    labels.put("url", url.getHost());
    owner.log("HTTP error: " + response.statusCode(), LogLevel.ERROR, labels);
    errors.add("HTTP error: " + response.statusCode());
    this.errors = errors;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    return node;
  }
}
