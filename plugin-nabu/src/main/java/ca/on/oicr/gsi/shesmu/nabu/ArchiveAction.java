package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;

public abstract class ArchiveAction<T extends NabuBaseArchiveDto> extends JsonParameterisedAction {

  protected final Definer<NabuPlugin> owner;
  static final ObjectMapper MAPPER = new ObjectMapper();
  protected List<String> errors = new ArrayList<>();
  public Optional<String> archiveNote;
  public String archiveTarget;
  public Set<String> archiveWith;
  public String identifier;
  public Long totalSize;
  public Long offsiteArchiveSize;
  public Long onsiteArchiveSize;
  public Set<String> limsIds;
  public Set<String> workflowRunIdsForOffsiteArchive;
  public Set<String> workflowRunIdsForVidarrArchival;
  protected static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  protected final ObjectNode parameters;
  protected final ObjectNode rootParameters = MAPPER.createObjectNode();
  protected Optional<String> authenticationHeader = Optional.empty();

  private static final Counter nabuRequestErrors =
      Counter.build(
              "shesmu_nabu_request_errors",
              "The number of errors trying to contact the Nabu web service.")
          .labelNames("target")
          .register();
  private static final LatencyHistogram NabuRequestTime =
      new LatencyHistogram(
          "shesmu_nabu_request_time",
          "The request time latency to launch a remote action.",
          "target");

  public ArchiveAction(Definer<NabuPlugin> owner, String actionTypeName) {
    super(actionTypeName);
    this.owner = owner;
    parameters = rootParameters.putObject("parameters");
  }

  protected abstract String actionType();

  protected abstract String identifierJsonFieldName();

  protected abstract String totalSizeJsonFieldName();

  protected abstract String entityLabel();

  protected abstract Class<T[]> dtoArrayClass();

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
    final ArchiveAction other = (ArchiveAction) obj;
    return identifier.equals(other.identifier)
        && Objects.equals(this.limsIds, other.limsIds)
        && Objects.equals(this.parameters, other.parameters);
  }

  private ActionState actionStatusFromArchive(T archive) {
    if (archive.getCreated() != null) {
      if (archive.getFilesLoadedIntoVidarrArchival() == null
          && archive.getFilesCopiedToOffsiteArchiveStagingDir() == null) {
        return ActionState.WAITING;
      } else if (archive.getCommvaultBackupJobId() != null
          // files copied to offsite staging dir is a prereq to a commvaultBackupJobId
          && archive.getFilesCopiedToOffsiteArchiveStagingDir() != null) {
        return ActionState.SUCCEEDED;
      } else {
        return ActionState.INFLIGHT;
      }
    }
    return ActionState.UNKNOWN;
  }

  protected ObjectNode createRequestJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put(identifierJsonFieldName(), identifier);
    node.put("archiveTarget", archiveTarget);
    archiveWith.forEach(node.putArray("archiveWith")::add);
    node.put(identifierJsonFieldName(), identifier);
    limsIds.forEach(node.putArray("limsIds")::add);
    workflowRunIdsForOffsiteArchive.forEach(node.putArray("workflowRunIdsForOffsiteArchive")::add);
    workflowRunIdsForVidarrArchival.forEach(node.putArray("workflowRunIdsForVidarrArchival")::add);
    node.set(
        "metadata", metadataToJson(archiveNote, totalSize, offsiteArchiveSize, onsiteArchiveSize));
    return node;
  }

  protected String createRequestBody() throws JsonProcessingException {
    return MAPPER.writeValueAsString(createRequestJson(MAPPER));
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
    return sendArchiveActionRequest(HTTP_CLIENT, baseUrl);
  }

  protected HttpRequest buildRequest(String baseUrl) throws JsonProcessingException {
    HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(createRequestBody());
    final String authentication = owner.get().NabuToken();
    authenticationHeader = Optional.ofNullable(authentication);

    final HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + "/" + entityLabel()));

    authenticationHeader.ifPresent(header -> builder.header("X-API-KEY", header));

    return builder
        .header("Content-type", "application/json")
        .header("Accept", "application/json")
        .timeout(Duration.ofMinutes(owner.get().timeout()))
        .POST(body)
        .build();
  }

  private ActionState sendArchiveActionRequest(HttpClient HTTP_CLIENT, String baseUrl) {
    HttpRequest request;
    final Map<String, String> labels = new TreeMap<>();
    labels.put("url", baseUrl);
    try {
      request = buildRequest(baseUrl);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }
    try (AutoCloseable timer = NabuRequestTime.start(baseUrl)) {
      HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
      if (response.statusCode() == 409) {
        nabuRequestErrors.labels(baseUrl).inc();
        try {
          this.showHTTPError(
              response,
              baseUrl,
              String.format(
                  "Attempted to resubmit archive request with conflicting data for %s %s",
                  entityLabel(), this.identifier));
        } catch (JsonProcessingException e) {
          this.errors.add("Additional error decoding Nabu response: " + e.getMessage());
        }
        return ActionState.HALP;
      } else if (response.statusCode() >= 400) {
        nabuRequestErrors.labels(baseUrl).inc();
        try {
          this.showHTTPError(response, baseUrl);
        } catch (JsonProcessingException e) {
          this.errors.add("Additional error decoding Nabu response: " + e.getMessage());
        }
        return ActionState.FAILED;
      } else if (response.statusCode() / 100 == 3) {
        String redirectLocation = response.headers().firstValue("Location").orElse(null);
        if (redirectLocation == null) {
          return ActionState.UNKNOWN;
        } else {
          return (sendArchiveActionRequest(HTTP_CLIENT, redirectLocation));
        }
      } else if (response.statusCode() == 201) {
        return ActionState.INFLIGHT;
      } else if (response.statusCode() == 200) {
        final T[] results = MAPPER.readValue(response.body(), dtoArrayClass());
        return actionStatusFromArchive(Arrays.stream(results).findFirst().get());
      } else {
        return ActionState.UNKNOWN;
      }
    } catch (Exception e) {
      e.printStackTrace();
      owner.log(
          "Error performing " + entityLabel() + " archiving action: " + e.getMessage(),
          LogLevel.ERROR,
          labels);
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

  protected void showHTTPError(HttpResponse<String> response, String url, String customLogMessage)
      throws UnsupportedOperationException, JsonProcessingException {
    final List<String> errors = new ArrayList<>();
    final Map<String, String> labels = new TreeMap<>();
    labels.put("url", url);
    owner.log(
        Objects.requireNonNullElseGet(
            customLogMessage, () -> "HTTP error: " + response.statusCode()),
        LogLevel.ERROR,
        labels);
    errors.add("HTTP error: " + response.statusCode());
    if (response.body() != null) {
      String responseBody = response.body();
      if (!responseBody.isEmpty()) {
        owner.log("HTTP response: " + responseBody, LogLevel.ERROR, labels);
        errors.add("Error: " + responseBody);
      }
    }
    this.errors = errors;
  }

  protected void showHTTPError(HttpResponse<String> response, String url)
      throws UnsupportedOperationException, JsonProcessingException {
    showHTTPError(response, url, null);
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.set("request", createRequestJson(mapper));
    node.put("type", actionType());
    node.set("parameters", parameters);
    errors.forEach(node.putArray("errors")::add);
    return node;
  }

  protected JsonNode metadataToJson(
      Optional<String> archiveNote,
      Long totalSize,
      Long offsiteArchiveSize,
      Long onsiteArchiveSize) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("archiveNote", archiveNote.orElse(null));
    node.put(totalSizeJsonFieldName(), totalSize);
    node.put("offsiteArchiveSize", offsiteArchiveSize);
    node.put("onsiteArchiveSize", onsiteArchiveSize);
    return node;
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, parameters, identifier, limsIds);
  }
}
