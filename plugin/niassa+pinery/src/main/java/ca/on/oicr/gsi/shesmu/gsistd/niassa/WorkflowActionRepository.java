package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

@MetaInfServices
public class WorkflowActionRepository implements ActionRepository {
	private class ServerInstance extends AutoUpdatingJsonFile<Configuration> {
		private List<ActionDefinition> actionDefinitions = Collections.emptyList();
		private Optional<Configuration> configuration = Optional.empty();

		public ServerInstance(Path fileName) {
			super(fileName, Configuration.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("SeqWare/Niassa Action Repository: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Filename", fileName().toString());
					configuration.ifPresent(c -> {
						renderer.line("JAR File", c.getJar());
						renderer.line("Settings", c.getSettings());
						renderer.line("Registered Workflows Count", c.getWorkflows().length);
					});

				}
			};
		}

		public Stream<ActionDefinition> queryActions() {
			return actionDefinitions.stream();
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			actionDefinitions = Stream.of(value.getWorkflows())//
					.peek(wc -> WorkflowAction.MAX_IN_FLIGHT.putIfAbsent(wc.getAccession(),
							new Semaphore(wc.getMaxInFlight())))//
					.<ActionDefinition>map(wc -> WorkflowAction.create(wc.getName(), wc.getType().type(),
							wc.getAccession(), wc.getPreviousAccessions(), value.getJar(), value.getSettings(),
							wc.getServices(), wc.getType().parameters()))//
					.collect(Collectors.toList());
			configuration = Optional.of(value);
			return Optional.empty();
		}
	}

	private final AutoUpdatingDirectory<ServerInstance> configurations = new AutoUpdatingDirectory<>(".seqware",
			ServerInstance::new);

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(ServerInstance::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(ServerInstance::queryActions);
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
		writer.println("    text(`Magic: ${a.magic}`),");
		writer.println("    text(`Input File Accessions: ${a.fileAccessions || 'None'}`),");
		writer.println("    text(`Parent Accessions: ${a.parentAccessions || 'None'}`),");
		writer.println("  ].concat(Object.entries(a.ini).map(i => text(`INI ${i[0]} = ${i[1]}`)))));");
	}

}
