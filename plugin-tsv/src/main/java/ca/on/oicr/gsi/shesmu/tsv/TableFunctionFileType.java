package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
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
@MetaInfServices(PluginFileType.class)
public class TableFunctionFileType extends PluginFileType<TableFunctionFile> {

  static final String EXTENSION = ".lookup";

  public TableFunctionFileType() {
    super(MethodHandles.lookup(), TableFunctionFile.class, EXTENSION);
  }

  @Override
  public TableFunctionFile create(Path filePath, String instanceName, Definer definer) {
    return new TableFunctionFile(filePath, instanceName, definer);
  }
}
