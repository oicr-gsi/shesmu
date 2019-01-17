package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;
import java.io.PrintStream;
import org.kohsuke.MetaInfServices;

/**
 * Create actions that mirror the actions of an existing Shesmu instance, but do nothing when
 * executed
 *
 * <p>This is for preparation of development servers
 */
@MetaInfServices(DefinitionRepository.class)
public class FakeRemoteDefinitionInstance
    extends FileBackedArbitraryDefinitionRepository<RemoteInstance> {

  public FakeRemoteDefinitionInstance() {
    super(RemoteInstance.class, ".fakeactions", RemoteInstance::new);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.print(
        "actionRender.set('fake', a => [title(a, `Fake ${a.name}`)].concat(jsonParameters(a)));");
  }
}
