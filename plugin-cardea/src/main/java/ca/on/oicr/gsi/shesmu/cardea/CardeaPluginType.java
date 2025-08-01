package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class CardeaPluginType extends PluginFileType<CardeaPlugin> {

  public CardeaPluginType() {
    super(MethodHandles.lookup(), CardeaPlugin.class, ".cardea", "cardea");
  }

  @Override
  public CardeaPlugin create(Path filePath, String instanceName, Definer<CardeaPlugin> definer) {
    return new CardeaPlugin(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // do nothing right now
  }
}
