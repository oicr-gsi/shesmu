package ca.on.oicr.gsi.shesmu.onlinereport;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.cromwell.LabelsResponse;
import ca.on.oicr.gsi.shesmu.cromwell.WorkflowIdAndStatus;
import ca.on.oicr.gsi.shesmu.cromwell.WorkflowQueryResponse;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.MultiPartBodyPublisher;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OnlineReport extends JsonPluginFile<Configuration> {

  private static final class OnlineReportParameter<I>
      extends CustomRefillerParameter<OnlineReportRefiller<I>, I> {

    private final String wdlName;

    public OnlineReportParameter(Entry<String, String> parameter, boolean pairsAsObjects) {
      super(
          parameter.getKey().replace(".", "_"),
          WdlInputType.parseString(parameter.getValue(), pairsAsObjects));
      wdlName = parameter.getKey();
    }

    @Override
    public void store(OnlineReportRefiller<I> refiller, Function<I, Object> function) {
      refiller.writers.add(
          (row, output) -> type().accept(new PackJsonObject(output, wdlName), function.apply(row)));
    }
  }

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge reportOk =
      Gauge.build("shesmu_onlinereport_ok", "Whether the report launched everything successfully.")
          .labelNames("filename")
          .register();
  private final Optional<Configuration> configuration = Optional.empty();
  private final Definer<OnlineReport> definer;
  private String labelKey;
  private String wdl;
  private String workflowName;

  public OnlineReport(Path fileName, String instanceName, Definer<OnlineReport> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    configuration.ifPresent(
        configuration -> {
          renderer.link("Cromwell", configuration.getCromwell(), configuration.getCromwell());
          renderer.link(
              "Workflow Name", configuration.getWorkflowName(), configuration.getWorkflowName());
        });
  }

  void processInput(Stream<Pair<String, ObjectNode>> input) {
    WorkflowQueryResponse workflowQueryResponse;
    // https://github.com/broadinstitute/cromwell/blob/develop/core/src/main/scala/cromwell/core/WorkflowState.scala
    var status = List.of("Running", "On Hold", "Submitted");
    var names = List.of(workflowName);
    var ok = new AtomicBoolean(true);

    // Query for all workflows that are Running with "workflowName"
    try {
      workflowQueryResponse =
          HTTP_CLIENT
              .send(
                  HttpRequest.newBuilder(
                          URI.create(
                              String.format(
                                  "%s/api/workflows/v1/query",
                                  configuration.orElseThrow().getCromwell())))
                      .header("Content-type", "application/json")
                      .POST(
                          BodyPublishers.ofString(
                              MAPPER.writeValueAsString(
                                  Stream.concat(
                                          status.stream().map(s -> Map.of("status", s)),
                                          names.stream().map(n -> Map.of("name", n)))
                                      .collect(Collectors.toList()))))
                      .build(),
                  new JsonBodyHandler<>(MAPPER, WorkflowQueryResponse.class))
              .body()
              .get();
    } catch (Exception e) {
      ok.set(false);
      e.printStackTrace();
      return;
    }

    // Retrieve label value for "labelKey" for all Running workflows with "workflowName"
    var runningJobs =
        workflowQueryResponse.getResults().stream()
            .map(
                r -> {
                  try {
                    return HTTP_CLIENT
                        .send(
                            HttpRequest.newBuilder(
                                    URI.create(
                                        String.format(
                                            "%s/v1/%s/labels",
                                            configuration.orElseThrow().getCromwell(), r.getId())))
                                .build(),
                            new JsonBodyHandler<>(MAPPER, LabelsResponse.class))
                        .body()
                        .get()
                        .getLabels()
                        .get(labelKey);
                  } catch (Exception e) {
                    ok.set(false);
                    e.printStackTrace();
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // Bail out if retrieving labels didn't work
    if (!ok.get()) {
      return;
    }

    var labels = MAPPER.createObjectNode();

    // Re-run everything not in runningJobs set
    var result =
        input
            .filter(i -> !runningJobs.contains(i.first()))
            .map(
                o -> {
                  labels.put(labelKey, o.first());
                  try {
                    final var cromwellBody =
                        new MultiPartBodyPublisher()
                            .addPart("workflowSource", wdl)
                            .addPart("workflowInputs", MAPPER.writeValueAsString(o.second()))
                            .addPart("workflowType", "WDL")
                            .addPart("workflowTypeVersion", "1.0")
                            .addPart("labels", MAPPER.writeValueAsString(labels));

                    return HTTP_CLIENT
                        .send(
                            HttpRequest.newBuilder()
                                .uri(
                                    URI.create(
                                        String.format(
                                            "%s/api/workflows/v1",
                                            configuration.orElseThrow().getCromwell())))
                                .header("Content-Type", cromwellBody.getContentType())
                                .POST(cromwellBody.build())
                                .build(),
                            new JsonBodyHandler<>(MAPPER, WorkflowIdAndStatus.class))
                        .body()
                        .get();
                  } catch (Exception e) {
                    ok.set(false);
                    e.printStackTrace();
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    reportOk.labels(fileName().toString()).set(ok.get() ? 1 : 0);
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    labelKey = configuration.getLabelKey();
    wdl = configuration.getWdl();
    workflowName = configuration.getWorkflowName();
    definer.clearRefillers();
    definer.defineRefiller(
        name(),
        configuration.getDescription(),
        new Definer.RefillDefiner() {
          @Override
          public <I> Definer.RefillInfo<I, OnlineReportRefiller<I>> info(Class<I> rowType) {
            return new Definer.RefillInfo<>() {
              @Override
              public OnlineReportRefiller<I> create() {
                return new OnlineReportRefiller<>(definer);
              }

              @Override
              public Stream<CustomRefillerParameter<OnlineReportRefiller<I>, I>> parameters() {
                return configuration.getParameters().entrySet().stream()
                    .map(
                        parameter ->
                            new OnlineReportParameter<>(
                                parameter, configuration.isPairsAsObjects()));
              }

              @Override
              public Class<? extends Refiller> type() {
                return OnlineReportRefiller.class;
              }
            };
          }
        });
    return Optional.empty();
  }
}
