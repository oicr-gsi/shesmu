package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class PineryPluginType extends PluginFileType<PinerySource> {

  public PineryPluginType() {
    super(MethodHandles.lookup(), PinerySource.class, ".pinery");
  }

  @Override
  public PinerySource create(Path filePath, String instanceName, Definer definer) {
    return new PinerySource(filePath, instanceName);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
