package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class ConfigmasterPluginType extends PluginFileType<ConfigmasterFile> {

  public ConfigmasterPluginType() {
    super(MethodHandles.lookup(), ConfigmasterFile.class, ".configmaster", "configmaster");
  }

  @Override
  public ConfigmasterFile create(Path fileName, String instanceName, Definer definer) {
    return new ConfigmasterFile(fileName, instanceName, definer);
  }
}
