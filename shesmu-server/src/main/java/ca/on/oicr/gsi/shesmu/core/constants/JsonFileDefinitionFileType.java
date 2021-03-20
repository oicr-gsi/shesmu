package ca.on.oicr.gsi.shesmu.core.constants;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/** Read constants from JSON files (and automatically reparse those files if they change) */
public final class JsonFileDefinitionFileType extends PluginFileType<JsonFileDefinitionFile> {

  public JsonFileDefinitionFileType() {
    super(MethodHandles.lookup(), JsonFileDefinitionFile.class, ".constants", "config");
  }

  @Override
  public JsonFileDefinitionFile create(
      Path filePath, String instanceName, Definer<JsonFileDefinitionFile> definer) {
    return new JsonFileDefinitionFile(filePath, instanceName, definer);
  }
}
