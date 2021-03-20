package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public final class LocalStructuredConfigFileType extends PluginFileType<LocalConfigurationFile> {

  public LocalStructuredConfigFileType() {
    super(MethodHandles.lookup(), LocalConfigurationFile.class, ".jsonconfig", "config");
  }

  @Override
  public LocalConfigurationFile create(
      Path filePath, String instanceName, Definer<LocalConfigurationFile> definer) {
    return new LocalConfigurationFile(filePath, instanceName, definer);
  }
}
