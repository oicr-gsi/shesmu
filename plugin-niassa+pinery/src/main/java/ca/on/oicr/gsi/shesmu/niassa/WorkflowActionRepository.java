package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Scanner;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class WorkflowActionRepository extends PluginFileType<NiassaServer> {

  public WorkflowActionRepository() {
    super(MethodHandles.lookup(), NiassaServer.class, ".niassa");
  }

  @Override
  public NiassaServer create(Path filePath, String instanceName, Definer<NiassaServer> definer) {
    return new NiassaServer(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    try (final Scanner input =
        new Scanner(WorkflowActionRepository.class.getResourceAsStream("renderer.js"), "UTF-8")) {
      writer.print(input.useDelimiter("\\Z").next());
    }
  }
}
