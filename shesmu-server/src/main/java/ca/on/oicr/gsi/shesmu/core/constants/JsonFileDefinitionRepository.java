package ca.on.oicr.gsi.shesmu.core.constants;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;

/**
 * Read constants from JSON files (and automatically reparse those files if they
 * change)
 */
@MetaInfServices(DefinitionRepository.class)
public class JsonFileDefinitionRepository extends FileBackedArbitraryDefinitionRepository<JsonFile> {

	public JsonFileDefinitionRepository() {
		super(JsonFile.class, ".constants", JsonFile::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		// No actions.
	}

}
