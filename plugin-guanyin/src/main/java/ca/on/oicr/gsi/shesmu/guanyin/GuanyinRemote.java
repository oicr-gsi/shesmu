package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.shesmu.plugin.json.*;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

public class GuanyinRemote extends JsonPluginFile<Configuration> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Optional<Configuration> configuration = Optional.empty();
  private final Definer<GuanyinRemote> definer;

  public GuanyinRemote(Path fileName, String instanceName, Definer<GuanyinRemote> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    configuration.ifPresent(
        configuration -> {
          renderer.link("DRMAAWS", configuration.getDrmaa(), configuration.getDrmaa());
          renderer.link("观音", configuration.getGuanyin(), configuration.getGuanyin());
          if (configuration.getCromwell() != null) {
            renderer.link("Cromwell", configuration.getCromwell(), configuration.getCromwell());
          }
          renderer.line("Script", configuration.getScript());
        });
  }

  public String drmaaPsk() {
    return configuration.get().getDrmaaPsk();
  }

  public String drmaaUrl() {
    return configuration.get().getDrmaa();
  }

  public String script() {
    return configuration.get().getScript();
  }

  public String cromwellUrl() {
    return configuration.get().getCromwell();
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    try (CloseableHttpResponse response =
        RunReport.HTTP_CLIENT.execute(
            new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
      definer.clearActions();
      for (final ReportDto report :
          RunReport.MAPPER.readValue(response.getEntity().getContent(), ReportDto[].class)) {
        if (!report.isValid()) {
          continue;
        }
        final long reportId = report.getId();
        final String actionName =
            report.getName() + "_" + report.getVersion().replaceAll("[^A-Za-z0-9_]", "_");
        final String description =
            String.format(
                "Runs report %s-%s (%d) on Guanyin instance defined in %s.",
                report.getName(), report.getVersion(), report.getId(), fileName());
        final String reportName =
            String.format("%s %s[%s]", report.getName(), report.getVersion(), report.getCategory());
        definer.defineAction(
            actionName,
            description,
            RunReport.class,
            () -> new RunReport(definer, reportId, reportName), //
            report
                .getPermittedParameters() //
                .entrySet()
                .stream() //
                .map(
                    e ->
                        new JsonParameter<>(
                            e.getKey(),
                            e.getValue().isRequired(),
                            Imyhat.parse(e.getValue().getType()))));
      }
      this.configuration = Optional.of(configuration);
      return Optional.of(60);
    } catch (final IOException e) {
      e.printStackTrace();
      return Optional.of(5);
    }
  }

  public String 观音Url() {
    return configuration.get().getGuanyin();
  }
}
