package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.regex.Pattern;
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
public class TSVTableFunctionFileType extends PluginFileType<TableFunctionFile> {

  private static final Pattern TAB = Pattern.compile("\t");

  public TSVTableFunctionFileType() {
    super(MethodHandles.lookup(), TableFunctionFile.class, ".lookup", "table");
  }

  @Override
  public TableFunctionFile create(
      Path filePath, String instanceName, Definer<TableFunctionFile> definer) {
    return new TableFunctionFile(filePath, instanceName, definer, TAB);
  }
}
