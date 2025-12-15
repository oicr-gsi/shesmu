package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class NabuPluginType extends PluginFileType<NabuPlugin> {

  public NabuPluginType() {
    super(MethodHandles.lookup(), NabuPlugin.class, ".nabu", "nabu");
  }

  @Override
  public NabuPlugin create(Path filePath, String instanceName, Definer<NabuPlugin> definer) {
    return new NabuPlugin(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.println("actionRender.set('archive-case-action', a => [");
    writer.println("  title(a, '${a.type} of ${a.caseIdentifier}'),");
    writer.println("  text(`Archive Target: ${a.archiveTarget}`)]);");
  }
}
