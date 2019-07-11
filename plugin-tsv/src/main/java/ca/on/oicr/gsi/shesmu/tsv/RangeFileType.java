package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class RangeFileType extends PluginFileType<RangeFile> {
  public RangeFileType() {
    super(MethodHandles.lookup(), RangeFile.class, ".range");
  }

  @Override
  public RangeFile create(Path filePath, String instanceName, Definer<RangeFile> definer) {
    return new RangeFile(filePath, instanceName, definer);
  }
}
