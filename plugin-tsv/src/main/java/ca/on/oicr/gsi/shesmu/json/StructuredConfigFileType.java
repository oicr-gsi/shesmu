package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class StructuredConfigFileType extends PluginFileType<StructuredConfigFile> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public StructuredConfigFileType() {
    super(MethodHandles.lookup(), StructuredConfigFile.class, ".jsonconfig", "config");
  }

  @Override
  public StructuredConfigFile create(
      Path filePath, String instanceName, Definer<StructuredConfigFile> definer) {
    return new StructuredConfigFile(filePath, instanceName, definer);
  }
}
