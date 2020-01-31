package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Scanner;
import org.kohsuke.MetaInfServices;

/**
 * Create actions that mirror the actions of an existing Shesmu instance, but do nothing when
 * executed
 *
 * <p>This is for preparation of development servers
 */
@MetaInfServices(PluginFileType.class)
public class FakeRemoteDefinitionInstance extends PluginFileType<RemoteInstance> {

  public FakeRemoteDefinitionInstance() {
    super(MethodHandles.lookup(), RemoteInstance.class, ".fakeactions");
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    try (final Scanner input =
        new Scanner(
            FakeRemoteDefinitionInstance.class.getResourceAsStream("renderer.js"), "UTF-8")) {
      writer.print(input.useDelimiter("\\Z").next());
    }
  }

  @Override
  public RemoteInstance create(
      Path filePath, String instanceName, Definer<RemoteInstance> definer) {
    return new RemoteInstance(filePath, instanceName, definer);
  }
}
