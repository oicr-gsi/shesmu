package ca.on.oicr.gsi.shesmu.rsconfig;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class IntervalsPlugin extends PluginFileType<IntervalsFile> {

  public IntervalsPlugin() {
    super(MethodHandles.lookup(), IntervalsFile.class, ".intervals");
  }

  @Override
  public IntervalsFile create(Path filePath, String instanceName, Definer<IntervalsFile> definer) {
    return new IntervalsFile(filePath, instanceName, definer);
  }
}
