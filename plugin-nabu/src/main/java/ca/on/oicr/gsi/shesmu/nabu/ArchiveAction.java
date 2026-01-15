package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
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
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public abstract class ArchiveAction<T extends NabuArchiveDto> extends JsonParameterisedAction {

  protected final Definer<NabuPlugin> owner;
  static final ObjectMapper MAPPER = new ObjectMapper();
  protected List<String> errors = new ArrayList<>();
  public Optional<String> archiveNote;
  public String archiveTarget;
  public Set<String> archiveWith;
  public String assayName;
  public String assayVersion;
  public String identifier;
  public Long totalSize;
  public Long offsiteArchiveSize;
  public Long onsiteArchiveSize;
  public Optional<Long> requisitionId;
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

  protected abstract String identifierJsonFieldName();

  protected abstract String totalSizeJsonFieldName();

  protected abstract String entityLabel();

  protected abstract Class<T[]> dtoArrayClass();

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
    return identifier.equals(other.identifier);
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

  protected String createRequestBody() {
    StringBuilder builder = new StringBuilder();
    builder.append("{ ");
    builder.append("\"archiveTarget\": \"");
    builder.append(this.archiveTarget);
    builder.append("\", ");
    builder.append("\"archiveWith\": [");
    builder.append(formatSetAsString(this.archiveWith));
    builder.append("], ");
    builder.append("\"");
    builder.append(identifierJsonFieldName());
    builder.append("\": \"");
    builder.append(this.identifier);
    builder.append("\", ");
    builder.append("\"requisitionId\": ");
    builder.append(this.requisitionId);
    builder.append(", ");
    builder.append("\"limsIds\": [");
    builder.append(formatSetAsString(limsIds));
    builder.append("], ");
    builder.append("\"workflowRunIdsForOffsiteArchive\": [");
    builder.append(formatSetAsString(workflowRunIdsForOffsiteArchive));
    builder.append("], ");
    builder.append("\"workflowRunIdsForVidarrArchival\": [");
    builder.append(formatSetAsString(workflowRunIdsForVidarrArchival));
    builder.append("], ");
    builder.append("\"metadata\": ");
    builder.append(
        metadataToJson(
            MAPPER,
            archiveNote,
            assayName,
            assayVersion,
            totalSize,
            offsiteArchiveSize,
            onsiteArchiveSize));
    builder.append(" }");
    return builder.toString();
  }

  protected String formatSetAsString(Set<String> set) {
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
      var response = HTTP_CLIENT.send(request, new JsonBodyHandler<>(MAPPER, dtoArrayClass()));
      if (response.statusCode() == 409) {
        owner.log(
            "Attempted to resubmit "
                + entityLabel()
                + " archive with conflicting data for "
                + entityLabel()
                + " "
                + this.identifier,
            LogLevel.ERROR,
            labels);
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
        final T[] results = response.body().get();
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

  protected void showHTTPError(HttpResponse<?> response, String url)
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
    node.put(identifierJsonFieldName(), identifier);
    node.put("requisitionId", requisitionId.orElse(null));
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
            totalSize,
            offsiteArchiveSize,
            onsiteArchiveSize));
    return node;
  }

  private JsonNode metadataToJson(
      ObjectMapper mapper,
      Optional<String> archiveNote,
      String assayName,
      String assayVersion,
      Long totalSize,
      Long offsiteArchiveSize,
      Long onsiteArchiveSize) {
    ObjectNode node = mapper.createObjectNode();
    node.put("archiveNote", archiveNote.orElse(null));
    node.put("assayName", assayName);
    node.put("assayVersion", assayVersion);
    node.put(totalSizeJsonFieldName(), totalSize);
    node.put("offsiteArchiveSize", offsiteArchiveSize);
    node.put("onsiteArchiveSize", onsiteArchiveSize);
    return node;
  }
}
