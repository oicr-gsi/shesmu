package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Scanner;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class WorkflowActionRepository extends PluginFileType<NiassaServer> {

  public WorkflowActionRepository() {
    super(MethodHandles.lookup(), NiassaServer.class, ".niassa", "niassa");
  }

  @Override
  public NiassaServer create(Path filePath, String instanceName, Definer<NiassaServer> definer) {
    return new NiassaServer(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // We're going to register a custom import converter for .niassawf files; It will need to know
    // all of the LIMS key types, so we're going to dump them into the output file first
    final ObjectNode types = NiassaServer.MAPPER.createObjectNode();
    for (final InputLimsKeyType type : InputLimsKeyType.values()) {
      types
          .putArray(type.name())
          .add(type.parameter().name())
          .add(type.parameter().type().descriptor());
    }
    try {
      writer.printf(
          "const niassaLimsKeyTypes = %s;\n", NiassaServer.MAPPER.writeValueAsString(types));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    try (final Scanner input =
        new Scanner(WorkflowActionRepository.class.getResourceAsStream("renderer.js"), "UTF-8")) {
      writer.print(input.useDelimiter("\\Z").next());
    }
  }
}
