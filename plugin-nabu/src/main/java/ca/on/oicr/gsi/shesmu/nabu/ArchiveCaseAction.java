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
          "o5assay_name$qsassay_version$qscase_total_size$qioffsite_archive_size$qionsite_archive_size$qi")
  // Attributes must be listed alphabetically!!
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
    final ArchiveCaseAction other = (ArchiveCaseAction) obj;
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
    return Objects.hash(owner, parameters, caseId, limsIds, requisitionId);
  }

  private String createRequestBody() throws JsonProcessingException {
    String body =
        "{ "
            + "\"caseIdentifier\": \""
            + this.caseId
            + "\", "
            + "\"requisitionId\": "
            + this.requisitionId
            + ", "
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
            + metadataToJson(MAPPER.createObjectNode(), metadata)
                .toString() // call toString here to ensure the quotation marks are preserved
            + "}";
    return body;
  }

  private String formatSetAsString(Set<String> set) {
    return set.stream().map(name -> ("\"" + name + "\"")).collect(Collectors.joining(","));
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    final Set<String> overloaded = services.isOverloaded("all", "nabu");
    if (!overloaded.isEmpty()) {
      this.errors =
          Collections.singletonList("Overloaded services: " + String.join(", ", overloaded));
      return ActionState.THROTTLED;
    }

    HttpRequest.BodyPublisher body;
    try {
      body = HttpRequest.BodyPublishers.ofString(createRequestBody());
      final String authentication = owner.get().NabuToken();
      authenticationHeader =
          authentication == null ? Optional.empty() : Optional.of(authentication);
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
    final String baseUrl = owner.get().NabuUrl();

    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/case"));

    authenticationHeader.ifPresent(header -> builder.header("X-API-KEY", header));

    final HttpRequest request =
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
        owner.log(
            "Attempted to resubmit case archive with conflicting data for case " + this.caseId,
            LogLevel.ERROR,
            new TreeMap<>());
        return ActionState.HALP;
      } else if (response.statusCode() >= 400) {
        NabuRequestErrors.labels(baseUrl).inc();
        showHTTPError(response, baseUrl);
        return ActionState.FAILED;
      } else if (response.statusCode() / 100 == 2) {
        return ActionState.SUCCEEDED;
      } else {
        return ActionState.UNKNOWN;
      }
    } catch (final Exception e) {
      e.printStackTrace();
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
    node.set("metadata", metadataToJson(mapper.createObjectNode(), metadata));
    return node;
  }

  private JsonNode metadataToJson(ObjectNode node, Tuple metadata) {
    node.put("assay_name", unwrapString(metadata, 0));
    node.put("assay_version", unwrapString(metadata, 1));
    node.put("case_total_size", unwrapLong(metadata, 2));
    node.put("offsite_archive_size", unwrapLong(metadata, 3));
    node.put("onsite_archive_size", unwrapLong(metadata, 4));
    return node;
  }

  private Long unwrapLong(Tuple metadata, Integer index) {
    if (metadata.get(index) == null) {
      return null;
    }
    Optional<Long> maybeItem = (Optional<Long>) metadata.get(index);
    return maybeItem.orElse(null);
  }

  private String unwrapString(Tuple metadata, Integer index) {
    if (metadata.get(index) == null) {
      return null;
    }
    Optional<String> maybeItem = (Optional<String>) metadata.get(index);
    return maybeItem.orElse(null);
  }
}
