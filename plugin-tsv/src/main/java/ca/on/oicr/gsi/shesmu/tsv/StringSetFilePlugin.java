package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

/**
 * Read constants from a file with one string per line (and automatically reparse those files if
 * they change)
 */
@MetaInfServices
public class StringSetFilePlugin extends PluginFileType<StringSetFile> {

  public StringSetFilePlugin() {
    super(MethodHandles.lookup(), StringSetFile.class, ".set");
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }

  @Override
  public StringSetFile create(Path filePath, String instanceName, Definer<StringSetFile> definer) {
    return new StringSetFile(filePath, instanceName);
  }
}
