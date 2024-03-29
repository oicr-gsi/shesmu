package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/** Converts a TSV file into a series of string expansions */
public class StringExpandFileType extends PluginFileType<StringExpandFile> {

  static final String EXTENSION = ".strexpand";

  public StringExpandFileType() {
    super(MethodHandles.lookup(), StringExpandFile.class, EXTENSION, "table");
  }

  @Override
  public StringExpandFile create(
      Path filePath, String instanceName, Definer<StringExpandFile> definer) {
    return new StringExpandFile(filePath, instanceName, definer);
  }
}
