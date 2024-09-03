package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonListBodyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ArchiveCaseAction extends JsonParameterisedAction {

  private final Definer<NabuPlugin> owner;
  private final ObjectNode parameters;
  private final ObjectNode rootParameters = MAPPER.createObjectNode();
  static final ObjectMapper MAPPER = new ObjectMapper();
  private List<String> errors = List.of();
  public String caseId;
  public long requisitionId;
  public Set<String> limsIds;
  public Set<String> workflowRunIdsForOffsiteArchive;
  public Set<String> workflowRunIdsForVidarrArchival;
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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

  public ArchiveCaseAction(Definer<NabuPlugin> owner) {
    super("nabu-plugin");
    this.owner = owner;
    parameters = rootParameters.putObject("parameters");
  }

  @ActionParameter
  public void caseId(String caseId) {
    this.caseId = caseId;
  }

  @ActionParameter
  public void requisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }

  @ActionParameter
  public void workflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  @ActionParameter
  public void workflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }

  @ActionParameter
  public void limsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  @Override
  public ObjectNode parameters() {
    return null;
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    final var other = (ArchiveCaseAction) obj;
    if (requisitionId != other.requisitionId) {
      return false;
    } else if (!limsIds.equals(other.limsIds)) {
      return false;
    }
    return caseId.equals(other.caseId);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(caseId.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      digest.accept(Utils.toBytes(requisitionId));
      digest.accept(MAPPER.writeValueAsBytes(limsIds));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + (owner == null ? 0 : owner.hashCode());
    result = prime * result + (caseId == null ? 0 : caseId.hashCode());
    result = prime * result + (limsIds == null ? 0 : limsIds.hashCode());
    result = prime * result + Long.hashCode(requisitionId);
    return result;
  }

  private String createRequestBody() {
    var body =
        "{ "
            + "caseIdentifier: \""
            + this.caseId
            + "\","
            + "requisitionId: \""
            + this.requisitionId
            + "\","
            + "limsIds: ["
            + String.join(",", limsIds)
            + "],"
            + "workflowRunIdsForOffsiteArchive: ["
            + String.join(",", workflowRunIdsForOffsiteArchive)
            + "],"
            + "workflowRunIdsForVidarrArchival: ["
            + String.join(",", workflowRunIdsForVidarrArchival)
            + "],"
            + "}";
    return body;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    final var overloaded = services.isOverloaded("all", "nabu");
    if (!overloaded.isEmpty()) {
      this.errors =
          Collections.singletonList("Overloaded services: " + String.join(", ", overloaded));
      return ActionState.THROTTLED;
    }

    HttpRequest.BodyPublisher body;
    try {
      body = HttpRequest.BodyPublishers.ofString(createRequestBody());
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
    var baseUrl = owner.get().NabuUrl();

    // final var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/case"));
    // final var createRequest = builder.header("Content-Type",
    // "application/json").POST(body).build();
    final var request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/case"))
            .header("Content-type", "application/json")
            .header("Accept", "application/json")
            .POST(body)
            .build();
    try (var timer = NabuRequestTime.start(owner.get().NabuUrl())) {
      var response =
          HTTP_CLIENT.send(request, new JsonListBodyHandler<>(MAPPER, NabuCaseArchiveDto.class));
      if (response.statusCode() == 409) {
        return ActionState.HALP;
      } else if (response.statusCode() / 100 != 2) {
        NabuRequestErrors.labels(owner.get().NabuUrl()).inc();
        return ActionState.FAILED;
      }
      final var results = response.body().get().collect(Collectors.toList());
      return actionStatusFromArchive(results.get(0));
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      NabuRequestErrors.labels(owner.get().NabuUrl()).inc();
      return ActionState.FAILED;
    }
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 1;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("type", "nabu-archive");
    node.put("caseId", caseId);
    node.put("requisitionId", requisitionId);
    limsIds.forEach(node.putArray("limsIds")::add);
    workflowRunIdsForOffsiteArchive.forEach(node.putArray("workflowRunIdsForOffsiteArchive")::add);
    workflowRunIdsForVidarrArchival.forEach(node.putArray("workflowRunIdsForVidarrArchival")::add);
    node.set("parameters", parameters);
    return node;
  }
}
