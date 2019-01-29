package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class RunScannerPluginType extends PluginFileType<RunScannerClient> {
  public RunScannerPluginType() {
    super(MethodHandles.lookup(), RunScannerClient.class, ".runscanner");
  }

  @Override
  public RunScannerClient create(Path filePath, String instanceName, Definer definer) {
    return new RunScannerClient(filePath, instanceName);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
