package ca.on.oicr.gsi.shesmu.guanyin;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;

/**
 * Converts Guanyin reports into actions
 */
@MetaInfServices(DefinitionRepository.class)
public class ReportDefinitionRepository extends FileBackedArbitraryDefinitionRepository<GuanyinFile> {

	private static final String EXTENSION = ".guanyin";

	public ReportDefinitionRepository() {
		super(GuanyinFile.class, EXTENSION, GuanyinFile::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print(
				"actionRender.set('guanyin-report', a => [title(a, `${a.reportName} – 观音 Report ${a.reportId}`)].concat(jsonParameters(a)));");
	}
}
