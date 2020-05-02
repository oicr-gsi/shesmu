package ca.on.oicr.gsi.shesmu.loki;

import ca.on.oicr.gsi.shesmu.plugin.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

/** Allows logging data to Loki */
@MetaInfServices
public class LokiPluginType extends PluginFileType<LokiPlugin> {

  @Override
  public LokiPlugin create(Path filePath, String instanceName, Definer<LokiPlugin> definer) {
    return new LokiPlugin(filePath, instanceName, definer);
  }

  public LokiPluginType() {
    super(MethodHandles.lookup(), LokiPlugin.class, ".loki");
  }
}
