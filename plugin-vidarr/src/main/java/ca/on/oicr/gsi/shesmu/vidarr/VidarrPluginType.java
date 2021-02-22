package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class VidarrPluginType extends PluginFileType<VidarrPlugin> {

  @ShesmuMethod(
      type = "o4id$sprovider$sstale$bversions$mss",
      description = "Adds signature information to a file provenance LIMS key.")
  public static Tuple sign(
      @ShesmuParameter(
              type = "o4id$sprovider$sstale$bversions$mss",
              description = "The external key from file provenance or Pinery provenance.")
          Tuple externalKey,
      @ShesmuParameter(description = "The signature from std::signature::sha1") String signature) {
    @SuppressWarnings("unchecked")
    final var versions = new TreeMap<>((Map<String, String>) externalKey.get(3));
    versions.put("shesmu-sha1", signature);
    return new Tuple(externalKey.get(0), externalKey.get(1), externalKey.get(2), versions);
  }

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
