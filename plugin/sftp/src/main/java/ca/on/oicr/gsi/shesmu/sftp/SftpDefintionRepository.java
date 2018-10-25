package ca.on.oicr.gsi.shesmu.sftp;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedMatchedDefinitionRepository;

@MetaInfServices(DefinitionRepository.class)
public class SftpDefintionRepository extends FileBackedMatchedDefinitionRepository<SftpServer> {

	private static final String EXTENSION = ".sftp";

	public SftpDefintionRepository() {
		super(SftpServer.class, EXTENSION, SftpServer::new);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		// No actions.
	}

}
