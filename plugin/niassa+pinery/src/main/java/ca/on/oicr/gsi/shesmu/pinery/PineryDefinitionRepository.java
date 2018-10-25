package ca.on.oicr.gsi.shesmu.pinery;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedMatchedDefinitionRepository;

@MetaInfServices(DefinitionRepository.class)
public class PineryDefinitionRepository extends FileBackedMatchedDefinitionRepository<PinerySource> {

	public PineryDefinitionRepository() {
		super(PinerySource.class, RemotePineryIUSRepository.EXTENSION, PinerySource::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		// No actions.
	}

}
