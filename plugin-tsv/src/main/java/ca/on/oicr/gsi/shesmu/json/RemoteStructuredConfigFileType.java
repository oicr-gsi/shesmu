package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public final class RemoteStructuredConfigFileType extends PluginFileType<RemoteConfigurationFile> {

  public RemoteStructuredConfigFileType() {
    super(MethodHandles.lookup(), RemoteConfigurationFile.class, ".remotejsonconfig", "config");
  }

  @Override
  public RemoteConfigurationFile create(
      Path filePath, String instanceName, Definer<RemoteConfigurationFile> definer) {
    return new RemoteConfigurationFile(filePath, instanceName, definer);
  }
}
