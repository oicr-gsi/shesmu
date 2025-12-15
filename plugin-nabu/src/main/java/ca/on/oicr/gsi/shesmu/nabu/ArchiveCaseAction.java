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
  public Optional<String> archiveNote;
  public String archiveTarget;
  public Set<String> archiveWith;
  public String assayName;
  public String assayVersion;
  public String caseIdentifier;
  public Long caseTotalSize;
  public Long offsiteArchiveSize;
  public Long onsiteArchiveSize;
  public long requisitionId;
  public Set<String> limsIds;
  public Set<String> workflowRunIdsForOffsiteArchive;
  public Set<String> workflowRunIdsForVidarrArchival;
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private final ObjectNode parameters;
  private final ObjectNode rootParameters = MAPPER.createObjectNode();
  private Optional<String> authenticationHeader = Optional.empty();

  private static final Counter nabuRequestErrors =
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
    super("archive-case-action");
    this.owner = owner;
    parameters = rootParameters.putObject("parameters");
  }

  @ActionParameter(name = "assay_name")
  public void assayName(String assayName) {
    this.assayName = assayName;
  }

  @ActionParameter(name = "assay_version")
  public void assayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }

  @ActionParameter(name = "archive_note")
  public void archiveNote(Optional<String> archiveNote) {
    this.archiveNote = archiveNote;
  }

  @ActionParameter(name = "archive_target")
  public void archiveTarget(String archiveTarget) {
    this.archiveTarget = archiveTarget;
  }

  @ActionParameter(name = "archive_with")
  public void archiveWith(Set<String> archiveWith) {
    this.archiveWith = archiveWith;
  }

  @ActionParameter(name = "case_identifier")
  public void caseId(String caseId) {
    this.caseIdentifier = caseId;
  }

  @ActionParameter(name = "case_total_size")
  public void caseTotalSize(Long caseTotalSize) {
    this.caseTotalSize = caseTotalSize;
  }

  @ActionParameter(name = "lims_ids")
  public void limsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  @ActionParameter(name = "offsite_archive_size")
  public void offsiteArchiveSize(Long offsiteArchiveSize) {
    this.offsiteArchiveSize = offsiteArchiveSize;
  }

  @ActionParameter(name = "onsite_archive_size")
  public void onsiteArchiveSize(Long onsiteArchiveSize) {
    this.onsiteArchiveSize = onsiteArchiveSize;
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
    return caseIdentifier.equals(other.caseIdentifier);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(caseIdentifier.getBytes(StandardCharsets.UTF_8));
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
    return Objects.hash(owner, parameters, caseIdentifier, limsIds, requisitionId);
  }

  private ActionState actionStatusFromArchive(NabuCaseArchiveDto caseArchive) {
    if (caseArchive.getCreated() != null) {
      if (caseArchive.getFilesLoadedIntoVidarrArchival() == null
          && caseArchive.getFilesCopiedToOffsiteArchiveStagingDir() == null) {
        return ActionState.WAITING;
      } else if (caseArchive.getCommvaultBackupJobId() != null
          // files copied to offsite staging dir is a prereq to a commvaultBackupJobId
          && caseArchive.getFilesCopiedToOffsiteArchiveStagingDir() != null) {
        return ActionState.SUCCEEDED;
      } else {
        return ActionState.INFLIGHT;
      }
    }
    return ActionState.UNKNOWN;
  }

  private String createRequestBody() {
    String body =
        "{ "
            + "\"archiveTarget\": \""
            + this.archiveTarget
            + "\", "
            + "\"archiveWith\": ["
            + formatSetAsString(this.archiveWith)
            + "], "
            + "\"caseIdentifier\": \""
            + this.caseIdentifier
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
            + metadataToJson(
                MAPPER,
                archiveNote,
                assayName,
                assayVersion,
                caseTotalSize,
                offsiteArchiveSize,
                onsiteArchiveSize)
            + " }";
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

    String baseUrl = owner.get().NabuUrl();
    return sendArchiveCaseActionRequest(HTTP_CLIENT, baseUrl);
  }

  private HttpRequest buildRequest(String baseUrl) throws JsonProcessingException {
    HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(createRequestBody());
    final String authentication = owner.get().NabuToken();
    authenticationHeader = authentication == null ? Optional.empty() : Optional.of(authentication);

    final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/case"));

    authenticationHeader.ifPresent(header -> builder.header("X-API-KEY", header));

    return builder
        .header("Content-type", "application/json")
        .header("Accept", "application/json")
        .timeout(Duration.ofMinutes(owner.get().timeout()))
        .POST(body)
        .build();
  }

  private ActionState sendArchiveCaseActionRequest(HttpClient HTTP_CLIENT, String baseUrl) {
    HttpRequest request;
    try {
      request = buildRequest(baseUrl);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
    try (var timer = NabuRequestTime.start(baseUrl)) {
      var response =
          HTTP_CLIENT.send(request, new JsonBodyHandler<>(MAPPER, NabuCaseArchiveDto[].class));
      if (response.statusCode() == 409) {
        owner.log(
            "Attempted to resubmit case archive with conflicting data for case "
                + this.caseIdentifier,
            LogLevel.ERROR,
            new TreeMap<>());
        return ActionState.HALP;
      } else if (response.statusCode() >= 400) {
        nabuRequestErrors.labels(baseUrl).inc();
        try {
          this.showHTTPError(response, baseUrl);
        } catch (JsonProcessingException e) {
          this.errors.add("Additional error decoding Nabu response");
        }
        return ActionState.FAILED;
      } else if (response.statusCode() / 100 == 3) {
        String redirectLocation = response.headers().firstValue("Location").orElse(null);
        if (redirectLocation == null) {
          return ActionState.UNKNOWN;
        } else {
          return (sendArchiveCaseActionRequest(HTTP_CLIENT, redirectLocation));
        }
      } else if (response.statusCode() == 201) {
        return ActionState.INFLIGHT;
      } else if (response.statusCode() == 200) {
        final NabuCaseArchiveDto[] results = response.body().get();
        return actionStatusFromArchive(Arrays.stream(results).findFirst().get());
      } else {
        return ActionState.UNKNOWN;
      }
    } catch (Exception e) {
      e.printStackTrace();
      final Map<String, String> labels = new TreeMap<>();
      labels.put("url", baseUrl);
      owner.log(
          "Error performing case archiving action: " + e.getMessage(), LogLevel.ERROR, labels);
      this.errors = Collections.singletonList(e.getMessage());
      nabuRequestErrors.labels(baseUrl).inc();
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
      throws UnsupportedOperationException, JsonProcessingException {
    final List<String> errors = new ArrayList<>();
    final Map<String, String> labels = new TreeMap<>();
    labels.put("url", url);
    owner.log("HTTP error: " + response.statusCode(), LogLevel.ERROR, labels);
    errors.add("HTTP error: " + response.statusCode());
    if (response.body() != null && !response.body().toString().isEmpty()) {
      owner.log("  error: " + response.body().toString(), LogLevel.ERROR, labels);
      errors.add("Error: " + MAPPER.writeValueAsString(response.body()));
    }
    nabuRequestErrors.labels(url).inc();
    this.errors = errors;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("type", "nabu-archive");
    node.put("archiveTarget", archiveTarget);
    archiveWith.forEach(node.putArray("archiveWith")::add);
    node.put("caseIdentifier", caseIdentifier);
    node.put("requisitionId", requisitionId);
    node.set("parameters", parameters);
    errors.forEach(node.putArray("errors")::add);
    limsIds.forEach(node.putArray("limsIds")::add);
    workflowRunIdsForOffsiteArchive.forEach(node.putArray("workflowRunIdsForOffsiteArchive")::add);
    workflowRunIdsForVidarrArchival.forEach(node.putArray("workflowRunIdsForVidarrArchival")::add);
    node.set(
        "metadata",
        metadataToJson(
            mapper,
            archiveNote,
            assayName,
            assayVersion,
            caseTotalSize,
            offsiteArchiveSize,
            onsiteArchiveSize));
    return node;
  }

  private JsonNode metadataToJson(
      ObjectMapper mapper,
      Optional<String> archiveNote,
      String assayName,
      String assayVersion,
      Long caseTotalSize,
      Long offsiteArchiveSize,
      Long onsiteArchiveSize) {
    ObjectNode node = mapper.createObjectNode();
    node.put("archiveNote", archiveNote.orElse(null));
    node.put("assayName", assayName);
    node.put("assayVersion", assayVersion);
    node.put("caseTotalSize", caseTotalSize);
    node.put("offsiteArchiveSize", offsiteArchiveSize);
    node.put("onsiteArchiveSize", onsiteArchiveSize);
    return node;
  }
}
