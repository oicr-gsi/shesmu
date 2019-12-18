package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.prometheus.*;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.WorkflowIdAndStatus;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Action to query/launch a report using Guanyin and Cromwell
 *
 * <p>The action will first query Guanyin to see if the report has been run previously. If not,
 * attempt to run it using Cromwell.
 */
public class RunReport extends JsonParameterisedAction {
  static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
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
          + "    Int record\n"
          + "  }\n"
          + "  command <<<\n"
          + " ~{script} ~{guanyin}/reportdb/record/~{record} \n"
          + "  >>>\n"
          + " runtime {\n"
          + " memory: \"~{memory} GB\"\n"
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
  private List<String> errors = Collections.emptyList();
  private Optional<Instant> externalTimestamp = Optional.empty();
  private final Supplier<GuanyinRemote> owner;
  private final ObjectNode parameters;
  private final long reportId;
  private final String reportName;
  private OptionalLong reportRecordId = OptionalLong.empty();
  private final ObjectNode rootParameters = MAPPER.createObjectNode();

  public RunReport(Supplier<GuanyinRemote> owner, long reportId, String reportName) {
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
    final RunReport other = (RunReport) obj;
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
    if (reportId != other.reportId) {
      return false;
    }
    return true;
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
    final int prime = 31;
    int result = 1;
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
  public ActionState perform(ActionServices services) {
    if (services.isOverloaded("all", "guanyin")) {
      return ActionState.THROTTLED;
    }

    // Query Guanyin to see if the record already exists
    StringEntity body;
    try {
      body =
          new StringEntity(MAPPER.writeValueAsString(rootParameters), ContentType.APPLICATION_JSON);
    } catch (final Exception e) {
      e.printStackTrace();
      this.errors = Collections.singletonList(e.getMessage());
      return ActionState.FAILED;
    }

    boolean create = false;
    final HttpPost request =
        new HttpPost(
            String.format(
                "%s/reportdb/record_parameters?report=%d", owner.get().观音Url(), reportId));
    request.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
    request.setEntity(body);
    try (AutoCloseable timer = 观音RequestTime.start(owner.get().观音Url());
        CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
      if (response.getStatusLine().getStatusCode() / 100 != 2) {
        showError(response, "Error from Guanyin: ");
        观音RequestErrors.labels(owner.get().观音Url()).inc();
        return ActionState.FAILED;
      }
      final RecordDto[] results =
          MAPPER.readValue(response.getEntity().getContent(), RecordDto[].class);
      if (results.length > 0) {
        final RecordDto record =
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
      final HttpPost createRequest =
          new HttpPost(
              String.format("%s/reportdb/record_start?report=%d", owner.get().观音Url(), reportId));
      createRequest.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
      createRequest.setEntity(body);
      try (AutoCloseable timer = 观音RequestTime.start(owner.get().观音Url());
          CloseableHttpResponse response = HTTP_CLIENT.execute(createRequest)) {
        if (response.getStatusLine().getStatusCode() / 100 != 2) {
          showError(response, "Error from Guanyin: ");
          观音RequestErrors.labels(owner.get().观音Url()).inc();
          return ActionState.FAILED;
        }
        reportRecordId =
            OptionalLong.of(
                MAPPER.readValue(response.getEntity().getContent(), CreateDto.class).getId());
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
      ApiClient apiClient = new ApiClient();
      apiClient.setBasePath(owner.get().cromwellUrl());
      WorkflowsApi wfApi = new WorkflowsApi(apiClient);
      if (cromwellId == null && create) {
        ObjectNode inputs = MAPPER.createObjectNode();
        inputs.put("guanyin.report.script", owner.get().script());
        inputs.put("guanyin.report.guanyin", owner.get().观音Url());
        inputs.put("guanyin.report.record", reportRecordId.getAsLong());
        inputs.put("guanyin.report.modules", owner.get().modules());
        inputs.put("guanyin.report.memory", owner.get().memory());
        ObjectNode labels = MAPPER.createObjectNode();
        labels.put(
            "external_id",
            String.format(
                "%s/reportdb/record/%d", owner.get().观音Url(), reportRecordId.getAsLong()));
        labels.put("type", reportName);
        cromwellId =
            wfApi.submit(
                "v1",
                WDL,
                null,
                false,
                MAPPER.writeValueAsString(inputs),
                null,
                null,
                null,
                null,
                null,
                "WDL",
                null,
                "1.0",
                MAPPER.writeValueAsString(labels),
                null);
        this.errors = Collections.emptyList();
      } else if (cromwellId != null) {
        cromwellId = wfApi.status("v1", cromwellId.getId());
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

  private void showError(CloseableHttpResponse response, String prefix)
      throws UnsupportedOperationException, IOException {
    final List<String> errors = new ArrayList<>();
    try (Scanner s = new Scanner(response.getEntity().getContent())) {
      s.useDelimiter("\\A");
      if (s.hasNext()) {
        final String message = s.next();
        System.err.print(prefix);
        System.err.println(message);
        errors.add(message);
      }
    }
    this.errors = errors;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
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
