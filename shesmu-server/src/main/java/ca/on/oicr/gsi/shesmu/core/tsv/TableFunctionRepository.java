package ca.on.oicr.gsi.shesmu.core.tsv;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;
import java.io.PrintStream;
import org.kohsuke.MetaInfServices;

/**
 * Converts a TSV file into a function
 *
 * <p>The row must be a Shesmu base type for the data in that column. The last column will be
 * treated as the return value and the first columns will be the parameters to match. Every
 * subsequent row is a set of parameters to check, which must either be a value or * to indicate a
 * wild card and a matching return value. If no rows match, the default value for that type is
 * returned.
 */
@MetaInfServices(DefinitionRepository.class)
public class TableFunctionRepository
    extends FileBackedArbitraryDefinitionRepository<TableFunctionFile> {

  static final String EXTENSION = ".lookup";

  public TableFunctionRepository() {
    super(TableFunctionFile.class, EXTENSION, TableFunctionFile::new);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
