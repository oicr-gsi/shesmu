package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.*;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/** Converts Guanyin reports into actions */
public class GuanyinReportPluginType extends PluginFileType<GuanyinRemote> {

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.print(
        "actionRender.set('guanyin-report', a => [title(a, `${a.reportName} – 观音 Report ${a.reportId}`)].concat(jsonParameters(a)).concat(a.cromwellUrl && a.cromwellId ? [link(a.cromwellUrl, 'Cromwell ID: ' + a.cromwellId)] : []));");
  }

  @Override
  public GuanyinRemote create(Path filePath, String instanceName, Definer<GuanyinRemote> definer) {
    return new GuanyinRemote(filePath, instanceName, definer);
  }

  public GuanyinReportPluginType() {
    super(MethodHandles.lookup(), GuanyinRemote.class, ".guanyin", "guanyin");
  }
}
