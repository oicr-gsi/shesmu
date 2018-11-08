package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;

@MetaInfServices(DefinitionRepository.class)
public class WorkflowActionRepository extends FileBackedArbitraryDefinitionRepository<NiassaServer> {

	static final String EXTENSION = ".niassa";

	public WorkflowActionRepository() {
		super(NiassaServer.class, EXTENSION, NiassaServer::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.println("actionRender.set('niassa', a => ");
		writer.println("  a.limsKeys.map((k, i) => [");
		writer.println("    text(`LIMS Key ${i} Provider: ${k.provider}`),");
		writer.println("    text(`LIMS Key ${i} ID: ${k.id}`),");
		writer.println("    text(`LIMS Key ${i} Version: ${k.version}`),");
		writer.println("    text(`LIMS Key ${i} Last Update: ${k.lastModified}`),");
		writer.println("  ]).reduce((acc, val) => acc.concat(val), [");
		writer.println("    title(a, `Workflow ${a.workflowAccession}`),");
		writer.println("    text(`Workflow Run Accession: ${a.workflowRunAccession || 'Unknown'}`),");
		writer.println("    text(`Major Olive Version: ${a.majorOliveVersion}`),");
		writer.println("    text(`Input File Accessions: ${a.fileAccessions || 'None'}`),");
		writer.println("    text(`Parent Accessions: ${a.parentAccessions || 'None'}`),");
		writer.println("  ].concat(Object.entries(a.ini).map(i => text(`INI ${i[0]} = ${i[1]}`)))));");
	}

}
