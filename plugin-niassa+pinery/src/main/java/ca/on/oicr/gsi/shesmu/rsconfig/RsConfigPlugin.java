package ca.on.oicr.gsi.shesmu.rsconfig;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class RsConfigPlugin extends PluginFileType<RsConfigFile> {

  public RsConfigPlugin() {
    super(MethodHandles.lookup(), RsConfigFile.class, ".rsconfig");
  }

  @Override
  public RsConfigFile create(Path filePath, String instanceName, Definer<RsConfigFile> definer) {
    return new RsConfigFile(filePath, instanceName, definer);
  }
}
