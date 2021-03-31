package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuanyinRemote extends JsonPluginFile<Configuration> {

  private class ReportsCache
      extends ValueCache<Stream<GuanyinReportValue>, Stream<GuanyinReportValue>> {
    public ReportsCache(Path fileName) {
      super("guanyin-reports " + fileName, 20, ReplacingRecord::new);
    }

    @Override
    protected Stream<GuanyinReportValue> fetch(Instant lastUpdated) throws Exception {
      if (configuration.isEmpty()) {
        return Stream.empty();
      }
      final var reportsResponse =
          RunReport.HTTP_CLIENT.send(
              HttpRequest.newBuilder(
                      URI.create(configuration.get().getGuanyin() + "/reportdb/reports"))
                  .version(Version.HTTP_1_1)
                  .build(),
              new JsonBodyHandler<>(RunReport.MAPPER, ReportDto[].class));
      final var recordsResponse =
          RunReport.HTTP_CLIENT.send(
              HttpRequest.newBuilder(
                      URI.create(configuration.get().getGuanyin() + "/reportdb/records"))
                  .version(Version.HTTP_1_1)
                  .build(),
              new JsonBodyHandler<>(RunReport.MAPPER, RecordDto[].class));
      final var reports =
          Stream.of(reportsResponse.body().get())
              .collect(Collectors.toMap(ReportDto::getId, Function.identity()));
      return Stream.of(recordsResponse.body().get())
          .map(dto -> new GuanyinReportValue(reports.get(dto.getReport()), dto));
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Optional<Configuration> configuration = Optional.empty();
  private final Definer<GuanyinRemote> definer;
  private final ReportsCache reports;

  public GuanyinRemote(Path fileName, String instanceName, Definer<GuanyinRemote> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
    reports = new ReportsCache(fileName);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    configuration.ifPresent(
        configuration -> {
          renderer.link("观音", configuration.getGuanyin(), configuration.getGuanyin());
          renderer.link("Cromwell", configuration.getCromwell(), configuration.getCromwell());
          renderer.line("Script", configuration.getScript());
        });
  }

  public int memory() {
    return configuration.get().getMemory();
  }

  public String modules() {
    return configuration.get().getModules();
  }

  public String script() {
    return configuration.get().getScript();
  }

  public String cromwellUrl() {
    return configuration.get().getCromwell();
  }

  @ShesmuInputSource
  public Stream<GuanyinReportValue> stream(boolean readStale) {
    return readStale ? reports.getStale() : reports.get();
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    try {
      final var response =
          RunReport.HTTP_CLIENT.send(
              HttpRequest.newBuilder(URI.create(configuration.getGuanyin() + "/reportdb/reports"))
                  .GET()
                  .version(Version.HTTP_1_1)
                  .build(),
              new JsonBodyHandler<>(RunReport.MAPPER, ReportDto[].class));
      definer.clearActions();
      for (final var report : response.body().get()) {
        if (!report.isValid()) {
          continue;
        }
        final var reportId = report.getId();
        final var actionName =
            report.getName() + "_" + report.getVersion().replaceAll("[^A-Za-z0-9_]", "_");
        final var description =
            String.format(
                "Runs report %s-%s (%d) on Guanyin instance defined in %s.",
                report.getName(), report.getVersion(), report.getId(), fileName());
        final var reportName =
            String.format("%s %s[%s]", report.getName(), report.getVersion(), report.getCategory());
        definer.defineAction(
            actionName,
            description,
            RunReport.class,
            () -> new RunReport(definer, reportId, reportName), //
            report.getPermittedParameters() //
                .entrySet().stream() //
                .map(
                    e ->
                        new JsonParameter<>(
                            e.getKey(),
                            e.getValue().isRequired(),
                            Imyhat.parse(e.getValue().getType()))));
      }
      this.configuration = Optional.of(configuration);
      return Optional.of(60);
    } catch (final IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.of(5);
    }
  }

  public String 观音Url() {
    return configuration.get().getGuanyin();
  }
}
