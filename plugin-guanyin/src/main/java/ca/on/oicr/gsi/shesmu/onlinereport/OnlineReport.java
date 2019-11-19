package ca.on.oicr.gsi.shesmu.onlinereport;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.WorkflowIdAndStatus;
import io.swagger.client.model.WorkflowQueryResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public class OnlineReport extends JsonPluginFile<Configuration> {
  static final ObjectMapper MAPPER = new ObjectMapper();
  private Optional<Configuration> configuration = Optional.empty();
  private static final Gauge reportOk =
      Gauge.build("shesmu_onlinereport_ok", "Whether the report launched everything successfully.")
          .labelNames("filename")
          .register();
  private final Definer<OnlineReport> definer;
  private String labelKey;
  private String wdl;
  private String workflowName;
  private WorkflowsApi wfApi;

  public OnlineReport(Path fileName, String instanceName, Definer<OnlineReport> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    configuration.ifPresent(
        configuration -> {
          renderer.link("Cromwell", configuration.getCromwell(), configuration.getCromwell());
          renderer.link(
              "Workflow Name", configuration.getWorkflowName(), configuration.getWorkflowName());
        });
  }

  void processInput(Stream<Pair<String, ObjectNode>> input) {
    WorkflowQueryResponse workflowQueryResponse;
    List<String> status = Collections.singletonList("Running"); // Other in progress statuses?
    List<String> names = Collections.singletonList(workflowName);
    AtomicBoolean ok = new AtomicBoolean(true);

    // Query for all workflows that are Running with "workflowName"
    try {
      workflowQueryResponse =
          wfApi.query(
              "v1", null, null, null, status, names, null, null, null, null, null, null, null);
    } catch (ApiException e) {
      ok.set(false);
      e.printStackTrace();
      return;
    }

    // Retrieve label value for "labelKey" for all Running workflows with "workflowName"
    Set<String> runningJobs =
        workflowQueryResponse
            .getResults()
            .stream()
            .map(
                r -> {
                  try {
                    return wfApi.labels("v1", r.getId()).getLabels().get(labelKey);
                  } catch (ApiException e) {
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

    ObjectNode labels = MAPPER.createObjectNode();

    // Re-run everything not in runningJobs set
    List<WorkflowIdAndStatus> result =
        input
            .filter(i -> !runningJobs.contains(i.first()))
            .map(
                o -> {
                  labels.put(labelKey, o.first());
                  try {
                    return wfApi.submit(
                        "v1",
                        wdl,
                        null,
                        false,
                        MAPPER.writeValueAsString(o.second()),
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
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(configuration.getCromwell());
    wfApi = new WorkflowsApi(apiClient);
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
            return new Definer.RefillInfo<I, OnlineReportRefiller<I>>() {
              @Override
              public OnlineReportRefiller<I> create() {
                return new OnlineReportRefiller<I>(definer);
              }

              @Override
              public Stream<CustomRefillerParameter<OnlineReportRefiller<I>, I>> parameters() {
                return configuration
                    .getParameters()
                    .entrySet()
                    .stream()
                    .map(
                        parameter ->
                            new CustomRefillerParameter<OnlineReportRefiller<I>, I>(
                                parameter.getKey().replace(".", "_"),
                                WdlInputType.parseString(parameter.getValue())) {
                              private final String wdlName = parameter.getKey();

                              @Override
                              public void store(
                                  OnlineReportRefiller<I> refiller, Function<I, Object> function) {
                                refiller.writers.add(
                                    (row, output) ->
                                        type()
                                            .accept(
                                                new PackJsonObject(output, wdlName),
                                                function.apply(row)));
                              }
                            });
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
