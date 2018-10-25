package ca.on.oicr.gsi.shesmu.runscanner;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedMatchedDefinitionRepository;

@MetaInfServices(DefinitionRepository.class)
public class RunScannerFunctionRepository extends FileBackedMatchedDefinitionRepository<RunScannerClient> {
	public RunScannerFunctionRepository() {
		super(RunScannerClient.class, ".runscanner", RunScannerClient::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		// No actions.
	}

}
