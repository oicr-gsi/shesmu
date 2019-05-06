package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
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
    writer.println("actionRender.set('niassa', a => ");
    writer.println("[");
    writer.println("    title(a, `Workflow ${a.workflowAccession}`),");
    writer.println("    text(`Workflow Run Accession: ${a.workflowRunAccession || 'Unknown'}`),");
    writer.println("    text(`Major Olive Version: ${a.majorOliveVersion}`),");
    writer.println("  ].concat(Object.entries(a.ini).map(i => text(`INI ${i[0]} = ${i[1]}`)))");
    writer.println("  .concat(a.limsKeys.flatMap((k, i) => [");
    writer.println("    text(`LIMS Key ${i} Provider: ${k.provider}`),");
    writer.println("    text(`LIMS Key ${i} ID: ${k.id}`),");
    writer.println("    text(`LIMS Key ${i} Version: ${k.version}`),");
    writer.println("    text(`LIMS Key ${i} Last Update: ${k.lastModified}`),");
    writer.println("  ]))");
    writer.println(
        "  .concat(a.matches.map(m => text(`Potential Match ${m.workflowRunAccession} [${m.state}]: ${m.match} ${m.stale ? '️🍞 Stale' : ''} ${m.extraLimsKeys ? '⬆️ Extra LIMS Keys' : ''} ${m.missingLimsKeys ? '️⬇️ Missing LIMS Keys' : ''} ${m.fileSubset ? '📂️️ Missing Files' : ''}`))));");
  }
}
