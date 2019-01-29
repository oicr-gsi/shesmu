package ca.on.oicr.gsi.shesmu.ratelimit.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
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
    writer.print(
        "actionRender.set('fake', a => [title(a, `Fake ${a.name}`)].concat(jsonParameters(a)));");
  }

  @Override
  public RemoteInstance create(Path filePath, String instanceName, Definer definer) {
    return new RemoteInstance(filePath, instanceName, definer);
  }
}
