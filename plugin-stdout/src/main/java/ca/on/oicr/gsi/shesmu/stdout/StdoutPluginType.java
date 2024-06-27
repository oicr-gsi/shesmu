package ca.on.oicr.gsi.shesmu.stdout;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/** Allows logging through the Definer to stdout */
public class StdoutPluginType extends PluginFileType<StdoutPlugin> {
  public StdoutPluginType() {
    super(MethodHandles.lookup(), StdoutPlugin.class, ".stdout", "stdout");
  }

  @Override
  public StdoutPlugin create(Path filePath, String instanceName, Definer<StdoutPlugin> definer) {
    return new StdoutPlugin(filePath, instanceName, definer);
  }
}
