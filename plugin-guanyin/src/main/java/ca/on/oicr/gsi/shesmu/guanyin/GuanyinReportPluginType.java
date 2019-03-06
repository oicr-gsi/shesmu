package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.*;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

/** Converts Guanyin reports into actions */
@MetaInfServices
public class GuanyinReportPluginType extends PluginFileType<GuanyinRemote> {

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.print(
        "actionRender.set('guanyin-report', a => [title(a, `${a.reportName} – 观音 Report ${a.reportId}`)].concat(jsonParameters(a)).concat(a.cromwellUrl ? [link(a.cromwellUrl, 'Cromwell')] : []));");
  }

  @Override
  public GuanyinRemote create(Path filePath, String instanceName, Definer<GuanyinRemote> definer) {
    return new GuanyinRemote(filePath, instanceName, definer);
  }

  public GuanyinReportPluginType() {
    super(MethodHandles.lookup(), GuanyinRemote.class, ".guanyin");
  }
}
