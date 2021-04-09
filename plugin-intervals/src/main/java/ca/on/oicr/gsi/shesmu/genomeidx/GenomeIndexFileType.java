package ca.on.oicr.gsi.shesmu.genomeidx;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GenomeIndexFileType extends PluginFileType<GenomeIndexFile> {

  public GenomeIndexFileType() {
    super(MethodHandles.lookup(), GenomeIndexFile.class, ".genomeidx", "genome");
  }

  @Override
  public GenomeIndexFile create(
      Path filePath, String instanceName, Definer<GenomeIndexFile> definer) {
    return new GenomeIndexFile(filePath, instanceName);
  }
}
