package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/**
 * Create actions that work on action definitions downloaded from an existing Shesmu instance, but
 * do nothing when executed
 *
 * <p>This is for preparation of development servers
 */
public class FakeLocalDefinitionInstance extends PluginFileType<LocalFile> {

  public FakeLocalDefinitionInstance() {
    super(MethodHandles.lookup(), LocalFile.class, ".fakeactiondefs", "fake");
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // Covered by its sibling
  }

  @Override
  public LocalFile create(Path filePath, String instanceName, Definer<LocalFile> definer) {
    return new LocalFile(filePath, instanceName, definer);
  }
}
