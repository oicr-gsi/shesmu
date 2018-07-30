package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;

@MetaInfServices
public class SeqWareActionRepository implements ActionRepository {
	private class SeqWareInstance extends AutoUpdatingJsonFile<Configuration> {
		private List<ActionDefinition> actionDefinitions = Collections.emptyList();

		private final Map<String, String> properties = new TreeMap<>();

		public SeqWareInstance(Path fileName) {
			super(fileName, Configuration.class);
			properties.put("file", fileName.toString());
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>("SeqWare Action Repository", properties);
		}

		public Stream<ActionDefinition> queryActions() {
			return actionDefinitions.stream();
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			properties.put("settings", value.getSettings());
			actionDefinitions = Stream.of(value.getWorkflows())//
					.peek(wc -> SeqWareWorkflowAction.MAX_IN_FLIGHT.putIfAbsent(wc.getAccession(),
							new Semaphore(wc.getMaxInFlight())))//
					.<ActionDefinition>map(wc -> SeqWareWorkflowAction.create(wc.getName(), wc.getType().type(),
							wc.getAccession(), wc.getPreviousAccessions(), value.getJar(), value.getSettings(),
							wc.getServices(), wc.getType().parameters()))//
					.collect(Collectors.toList());
			return Optional.empty();
		}
	}

	private final AutoUpdatingDirectory<SeqWareInstance> configurations = new AutoUpdatingDirectory<>(".seqware",
			SeqWareInstance::new);

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(SeqWareInstance::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(SeqWareInstance::queryActions);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.println("actionRender.set('seqware', a => [");
		writer.println("    title(a, 'SeqWare Workflow ${a.workflowAccession}'),");
		writer.println("    text(`Magic: ${a.magic}`),");
		writer.println("    text(`Input Files: ${a.inputFiles}`),");
		writer.println("  ].concat(a.limsKeys.flatMap((k, i) => [");
		writer.println("    text(`LIMS Key ${i} Provider: ${k.provider}`),");
		writer.println("    text(`LIMS Key ${i} ID: ${k.id}`),");
		writer.println("    text(`LIMS Key ${i} Version: ${k.version}`),");
		writer.println("    text(`LIMS Key ${i} Last Update: ${k.lastModified}`),");
		writer.println("  ])).concat(Object.entries(a.ini).map(i => text(`INI ${i[0]} = ${i[1]}`))));");
	}

}
