package ca.on.oicr.gsi.shesmu.variables.provenance;

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

}
