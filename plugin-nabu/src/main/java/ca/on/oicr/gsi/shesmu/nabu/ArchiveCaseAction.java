package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ArchiveCaseAction extends JsonParameterisedAction {

  private final Definer<NabuPlugin> owner;
  static final ObjectMapper MAPPER = new ObjectMapper();
  private List<String> errors = List.of();
  public String caseId;
  public long requisitionId;
  public Set<String> limsIds;
  public Tuple metadata;
  public Set<String> workflowRunIdsForOffsiteArchive;
  public Set<String> workflowRunIdsForVidarrArchival;
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private final ObjectNode parameters;
  private final ObjectNode rootParameters = MAPPER.createObjectNode();
  private Optional<String> authenticationHeader = Optional.empty();

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

  @ActionParameter(name = "case_identifier")
  public void caseId(String caseId) {
    this.caseId = caseId;
  }

  @ActionParameter(name = "lims_ids")
  public void limsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  @ActionParameter(
      name = "metadata",
      type =
          "o5case_total_size$qioffsite_archive_size$qionsite_archive_size$qiassay_name$qsassay_version$qs")
  // If this object's size changes, the serialization code needs to change as well
  public void metadata(Tuple metadata) {
    this.metadata = metadata;
  }

  @ActionParameter(name = "requisition_id")
  public void requisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }

  @ActionParameter(name = "workflow_run_ids_for_offsite_archive")
  public void workflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  @ActionParameter(name = "workflow_run_ids_for_vidarr_archival")
  public void workflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }

  @Override
  public ObjectNode parameters() {
    return parameters;
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
    if (requisitionId != other.requisitionId) {
      return false;
    } else if (!limsIds.equals(other.limsIds)) {
      return false;
    } else if (!parameters.equals(other.parameters)) {
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
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, parameters, caseId, limsIds, requisitionId, metadata);
  }

  private String createRequestBody() throws JsonProcessingException {
    return "{ "
        + "\"caseIdentifier\": \""
        + this.caseId
        + "\", "
        + "\"requisitionId\": \""
        + this.requisitionId
        + "\", "
        + "\"limsIds\": ["
        + formatSetAsString(limsIds)
        + "], "
        + "\"workflowRunIdsForOffsiteArchive\": ["
        + formatSetAsString(workflowRunIdsForOffsiteArchive)
        + "], "
        + "\"workflowRunIdsForVidarrArchival\": ["
        + formatSetAsString(workflowRunIdsForVidarrArchival)
        + "], "
        + "\"metadata\": "
        + MAPPER.writeValueAsString(metadata.toString())
        + "}";
  }

  private String formatSetAsString(Set<String> set) {
    return set.stream().map(name -> ("\"" + name + "\"")).collect(Collectors.joining(","));
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
      final var authentication = owner.get().NabuToken();
      authenticationHeader =
          authentication == null ? Optional.empty() : Optional.of(authentication);
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
    final var baseUrl = owner.get().NabuUrl();

    final var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/case"));

    authenticationHeader.ifPresent(header -> builder.header("X-API-KEY", header));

    final var request =
        builder
            .header("Content-type", "application/json")
            .header("Accept", "application/json")
            .POST(body)
            .build();

    owner.log("NABU REQUEST: " + request, LogLevel.DEBUG, null);

    try (var timer = NabuRequestTime.start(baseUrl)) {
      var response =
          HTTP_CLIENT.send(request, new JsonBodyHandler<>(MAPPER, NabuCaseArchiveDto.class));
      if (response.statusCode() == 409) {
        return ActionState.HALP;
      } else if (response.statusCode() / 100 > 4) {
        NabuRequestErrors.labels(baseUrl).inc();
        showHTTPError(response, baseUrl);
        return ActionState.FAILED;
      } else if (response.statusCode() / 100 == 2) {
        return ActionState.SUCCEEDED;
      } else {
        return ActionState.UNKNOWN;
      }
    } catch (final Exception e) {
      final Map<String, String> labels = new TreeMap<>();
      labels.put("url", baseUrl);
      owner.log(
          "Error performing case archiving action: " + e.getMessage(), LogLevel.ERROR, labels);
      this.errors = Collections.singletonList(e.getMessage());
      NabuRequestErrors.labels(baseUrl).inc();
      return ActionState.FAILED;
    }
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 10;
  }

  private void showHTTPError(HttpResponse<?> response, String url)
      throws UnsupportedOperationException, IOException {
    final List<String> errors = new ArrayList<>();
    final Map<String, String> labels = new TreeMap<>();
    labels.put("url", url);
    owner.log("HTTP error: " + response.statusCode(), LogLevel.ERROR, labels);
    errors.add("HTTP error: " + response.statusCode());
    NabuRequestErrors.labels(url).inc();
    this.errors = errors;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("type", "nabu-archive");
    node.put("case_id", caseId);
    node.put("requisition_id", requisitionId);
    node.set("parameters", parameters);
    errors.forEach(node.putArray("errors")::add);
    limsIds.forEach(node.putArray("lims_ids")::add);
    workflowRunIdsForOffsiteArchive.forEach(
        node.putArray("workflow_run_ids_for_offsite_archive")::add);
    workflowRunIdsForVidarrArchival.forEach(
        node.putArray("workflow_run_ids_for_vidarr_archival")::add);
    node.set("metadata", metadataToJson(mapper, metadata));
    return node;
  }

  private JsonNode metadataToJson(ObjectMapper mapper, Tuple metadata) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("case_total_size", metadata.get(0) == null ? null : ((Integer) metadata.get(0)));
    node.put("offsite_archive_size", metadata.get(1) == null ? null : ((Integer) metadata.get(1)));
    node.put("onsite_archive_size", metadata.get(2) == null ? null : ((Integer) metadata.get(2)));
    node.put("assay_name", metadata.get(3) == null ? null : ((String) metadata.get(3)));
    node.put("assay_version", metadata.get(4) == null ? null : ((String) metadata.get(4)));
    return node;
  }
}
