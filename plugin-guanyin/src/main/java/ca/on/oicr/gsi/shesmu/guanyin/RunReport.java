package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.cromwell.WorkflowIdAndStatus;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Action to query/launch a report using Guanyin and Cromwell
 *
 * <p>The action will first query Guanyin to see if the report has been run previously. If not,
 * attempt to run it using Cromwell.
 */
public class RunReport extends JsonParameterisedAction {
  private static final ActionCommand<RunReport> FORCE_RELAUNCH_COMMAND =
      new ActionCommand<>(
          RunReport.class,
          "GUANYIN-FORCE-RELAUNCH",
          FrontEndIcon.ARROW_REPEAT,
          "Relaunch on Cromwell",
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected Response execute(RunReport action, Optional<String> user) {
          if (action.reportRecordId.isPresent()) {
            action.forceRelaunch = true;
            return Response.RESET;
          }
          return Response.IGNORED;
        }
      };
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String WDL =
      "version 1.0\n"
          + "workflow guanyin {\n"
          + "  call report\n"
          + "}\n"
          + "task report {\n"
          + "  input {\n"
          + "    String script\n"
          + "    String guanyin\n"
          + "    String modules\n"
          + "    Int memory\n"
          + "    Int timeout\n"
          + "    Int record\n"
          + "  }\n"
          + "  command <<<\n"
          + " ~{script} ~{guanyin}/reportdb/record/~{record} \n"
          + "  >>>\n"
          + " runtime {\n"
          + " memory: \"~{memory} GB\"\n"
          + " timeout: timeout\n"
          + " modules: \"~{modules}\"\n"
          + " }\n"
          + "}\n";
  private static final Counter 观音RequestErrors =
      Counter.build(
              "shesmu_guanyin_request_errors",
              "The number of errors trying to countact the Guanyin web service.")
          .labelNames("target")
          .register();
  private static final LatencyHistogram 观音RequestTime =
      new LatencyHistogram(
          "shesmu_guanyin_request_time",
          "The request time latency to launch a remote action.",
          "target");

  static {
    MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  private WorkflowIdAndStatus cromwellId;
  private List<String> errors = List.of();
  private Optional<Instant> externalTimestamp = Optional.empty();
  private boolean forceRelaunch;
  private final Definer<GuanyinRemote> owner;
  private final ObjectNode parameters;
  private final long reportId;
  private final String reportName;
  private OptionalLong reportRecordId = OptionalLong.empty();
  private final ObjectNode rootParameters = MAPPER.createObjectNode();

  public RunReport(Definer<GuanyinRemote> owner, long reportId, String reportName) {
    super("guanyin-report");
    this.owner = owner;
    this.reportId = reportId;
    this.reportName = reportName;
    parameters = rootParameters.putObject("parameters");
  }

  private ActionState actionStatusFromCromwell(WorkflowIdAndStatus id) {
    if (id == null || id.getStatus() == null) {
      return ActionState.UNKNOWN;
    }
    switch (id.getStatus()) {
      case "Submitted":
        return ActionState.WAITING;
      case "Running":
        return ActionState.INFLIGHT;
      case "Aborting":
        return ActionState.FAILED;
      case "Aborted":
        return ActionState.FAILED;
      case "Failed":
        return ActionState.FAILED;
      case "Succeeded":
        return ActionState.SUCCEEDED;
    }
    return ActionState.UNKNOWN;
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return reportRecordId.isPresent() ? Stream.of(FORCE_RELAUNCH_COMMAND) : Stream.empty();
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
    final var other = (RunReport) obj;
    if (owner != other.owner) {
      return false;
    }
    if (parameters == null) {
      if (other.parameters != null) {
        return false;
      }
    } else if (!parameters.equals(other.parameters)) {
      return false;
    }
    return reportId == other.reportId;
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return externalTimestamp;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(Utils.toBytes(reportId));
    try {
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + (owner == null ? 0 : owner.hashCode());
    result = prime * result + (parameters == null ? 0 : parameters.hashCode());
    result = prime * result + Long.hashCode(reportId);
    return result;
  }

  @Override
  public ObjectNode parameters() {
    return parameters;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    final var overloaded = services.isOverloaded("all", "guanyin");
    if (!overloaded.isEmpty()) {
      errors = Collections.singletonList("Overloaded services: " + String.join(", ", overloaded));
      return ActionState.THROTTLED;
    }

    // Query Guanyin to see if the record already exists
    BodyPublisher body;
    try {
      body = BodyPublishers.ofString(MAPPER.writeValueAsString(rootParameters));
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }

    var create = false;
    final var request =
        HttpRequest.newBuilder(
                URI.create(
                    String.format(
                        "%s/reportdb/record_parameters?report=%d", owner.get().观音Url(), reportId)))
            .header("Content-type", "application/json")
            .header("Accept", "application/json")
            .version(Version.HTTP_1_1)
            .POST(body)
            .build();
    try (var timer = 观音RequestTime.start(owner.get().观音Url())) {
      var response = HTTP_CLIENT.send(request, new JsonBodyHandler<>(MAPPER, RecordDto[].class));
      if (response.statusCode() / 100 != 2) {
        showError(response, request.uri());
        观音RequestErrors.labels(owner.get().观音Url()).inc();
        return ActionState.FAILED;
      }
      final var results = response.body().get();
      if (results.length > 0) {
        final var record =
            Stream.of(results).max(Comparator.comparing(RecordDto::getGenerated)).get();
        reportRecordId = OptionalLong.of(record.getId());
        externalTimestamp = Optional.of(ZonedDateTime.parse(record.getGenerated()).toInstant());
        if (record.isFinished()) {
          return ActionState.SUCCEEDED;
        }
      } else {
        create = true;
      }
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      观音RequestErrors.labels(owner.get().观音Url()).inc();
      return ActionState.FAILED;
    }
    // At this point, either it exists and isn't complete or it doesn't exist.
    // Create it if it doesn't exist
    if (create) {
      final var createRequest =
          HttpRequest.newBuilder(
                  URI.create(
                      String.format(
                          "%s/reportdb/record_start?report=%d", owner.get().观音Url(), reportId)))
              .header("Content-type", "application/json")
              .header("Accept", "application/json")
              .version(Version.HTTP_1_1)
              .POST(body)
              .build();
      try (var timer = 观音RequestTime.start(owner.get().观音Url())) {
        var response =
            HTTP_CLIENT.send(createRequest, new JsonBodyHandler<>(MAPPER, CreateDto.class));
        if (response.statusCode() / 100 != 2) {
          showError(response, request.uri());
          观音RequestErrors.labels(owner.get().观音Url()).inc();
          return ActionState.FAILED;
        }
        reportRecordId = OptionalLong.of(response.body().get().getId());
        externalTimestamp = Optional.of(Instant.now());
      } catch (final Exception e) {
        e.printStackTrace();
        this.errors = Collections.singletonList(e.getMessage());
        观音RequestErrors.labels(owner.get().观音Url()).inc();
        return ActionState.FAILED;
      }
    }
    // Now that exists, try to run it via Cromwell if configured
    try {
      if (cromwellId == null && create || forceRelaunch) {
        forceRelaunch = false;
        var inputs = MAPPER.createObjectNode();
        inputs.put("guanyin.report.script", owner.get().script());
        inputs.put("guanyin.report.guanyin", owner.get().观音Url());
        inputs.put("guanyin.report.record", reportRecordId.getAsLong());
        inputs.put("guanyin.report.modules", owner.get().modules());
        inputs.put("guanyin.report.memory", owner.get().memory());
        inputs.put("guanyin.report.timeout", owner.get().timeout());
        var labels = MAPPER.createObjectNode();
        labels.put(
            "external_id",
            String.format(
                "%s/reportdb/record/%d", owner.get().观音Url(), reportRecordId.getAsLong()));
        labels.put("type", reportName);

        final var cromwellBody =
            new MultiPartBodyPublisher()
                .addPart("workflowSource", WDL)
                .addPart("workflowInputs", MAPPER.writeValueAsString(inputs))
                .addPart("workflowType", "WDL")
                .addPart("workflowTypeVersion", "1.0")
                .addPart("labels", MAPPER.writeValueAsString(labels));

        cromwellId =
            HTTP_CLIENT
                .send(
                    HttpRequest.newBuilder()
                        .uri(
                            URI.create(
                                String.format("%s/api/workflows/v1", owner.get().cromwellUrl())))
                        .header("Content-Type", cromwellBody.getContentType())
                        .version(Version.HTTP_1_1)
                        .POST(cromwellBody.build())
                        .build(),
                    new JsonBodyHandler<>(MAPPER, WorkflowIdAndStatus.class))
                .body()
                .get();
        this.errors = Collections.emptyList();
      } else if (cromwellId != null) {
        cromwellId =
            HTTP_CLIENT
                .send(
                    HttpRequest.newBuilder(
                            URI.create(
                                String.format(
                                    "%s/api/workflows/v1/%s/status",
                                    owner.get().cromwellUrl(), cromwellId.getId())))
                        .version(Version.HTTP_1_1)
                        .GET()
                        .build(),
                    new JsonBodyHandler<>(MAPPER, WorkflowIdAndStatus.class))
                .body()
                .get();
        this.errors = Collections.emptyList();
      } else {
        this.errors =
            Collections.singletonList("Report has already been launched but ID is unknown.");
      }
      return actionStatusFromCromwell(cromwellId);
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
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
    node.put("type", "guanyin-report");
    node.put("reportName", reportName);
    node.put("reportId", reportId);
    node.put("script", owner.get().script());
    node.set("parameters", parameters);
    this.errors.forEach(node.putArray("errors")::add);
    if (cromwellId != null) {
      node.put(
          "cromwellUrl",
          String.format(
              "%s/api/workflows/v1/%s/status", owner.get().cromwellUrl(), cromwellId.getId()));
      node.put("cromwellId", cromwellId.getId());
    }
    reportRecordId.ifPresent(
        id -> node.put("url", String.format("%s/reportdb/record/%d", owner.get().观音Url(), id)));
    return node;
  }
}
