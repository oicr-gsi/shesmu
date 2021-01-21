package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class VidarrPluginType extends PluginFileType<VidarrPlugin> {

  public VidarrPluginType() {
    super(MethodHandles.lookup(), VidarrPlugin.class, ".vidarr", "vidarr");
  }

  @Override
  public VidarrPlugin create(Path filePath, String instanceName, Definer<VidarrPlugin> definer) {
    return new VidarrPlugin(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    try (final Scanner input =
        new Scanner(
            VidarrPluginType.class.getResourceAsStream("renderer.js"), StandardCharsets.UTF_8)) {
      writer.print(input.useDelimiter("\\Z").next());
    }
  }
}
