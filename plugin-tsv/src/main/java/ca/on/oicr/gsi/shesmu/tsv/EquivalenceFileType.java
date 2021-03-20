package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class EquivalenceFileType extends PluginFileType<EquivalenceFile> {

  public EquivalenceFileType() {
    super(MethodHandles.lookup(), EquivalenceFile.class, ".equiv", "equiv");
  }

  @Override
  public EquivalenceFile create(
      Path filePath, String instanceName, Definer<EquivalenceFile> definer) {
    return new EquivalenceFile(filePath, instanceName, definer);
  }
}
