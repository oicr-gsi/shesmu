package ca.on.oicr.gsi.shesmu.core.constants;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedMatchedDefinitionRepository;
import java.io.PrintStream;
import org.kohsuke.MetaInfServices;

/**
 * Read constants from a file with one string per line (and automatically reparse those files if
 * they change)
 */
@MetaInfServices(DefinitionRepository.class)
public class StringSetFilePlugin extends FileBackedMatchedDefinitionRepository<StringSetFile> {

  public StringSetFilePlugin() {
    super(StringSetFile.class, ".set", StringSetFile::new);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
