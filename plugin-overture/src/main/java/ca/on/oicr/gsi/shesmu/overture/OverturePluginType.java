package ca.on.oicr.gsi.shesmu.overture;

import ca.on.oicr.gsi.shesmu.niassa.WorkflowActionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Scanner;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class OverturePluginType extends PluginFileType<OverturePlugin> {

  public OverturePluginType() {
    super(MethodHandles.lookup(), OverturePlugin.class, ".overture");
  }

  @Override
  public OverturePlugin create(
      Path filePath, String instanceName, Definer<OverturePlugin> definer) {
    return new OverturePlugin(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    try (final Scanner input =
        new Scanner(WorkflowActionRepository.class.getResourceAsStream("renderer.js"), "UTF-8")) {
      writer.print(input.useDelimiter("\\Z").next());
    }
  }
}
