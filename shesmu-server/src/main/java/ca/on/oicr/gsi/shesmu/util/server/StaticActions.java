package ca.on.oicr.gsi.shesmu.util.server;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionConsumer;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;

public class StaticActions implements LoadedConfiguration {
	private class StaticActionFile extends AutoUpdatingJsonFile<StaticAction[]> {
		int lastCount;

		public StaticActionFile(Path fileName) {
			super(fileName, StaticAction[].class);
		}

		@Override
		protected Optional<Integer> update(StaticAction[] actions) {
			lastCount = actions.length;
			int success = 0;
			boolean retry = false;
			for (final StaticAction action : actions) {
				if (!runners.containsKey(action.getAction())) {
					final Optional<ActionRunner> runner = definitions.get()//
							.filter(definition -> definition.name().equals(action.getAction()))//
							.findFirst()//
							.map(ActionRunnerCompiler::new)//
							.map(ActionRunnerCompiler::compile)//
							.filter(Objects::nonNull);

					runner.ifPresent(d -> runners.put(action.getAction(), d));
					if (!runner.isPresent()) {
						retry = true;
						continue;
					}
				}
				try {
					final Action result = runners.get(action.getAction()).run(action.getParameters());
					if (result != null) {
						sink.accept(result, fileName().toString(), 0, 0, Instant.now().getEpochSecond());
						success++;
					} else {
						retry = true;
					}
				} catch (final Exception e) {
					e.printStackTrace();
					retry = true;
				}
			}
			totalCount.labels(fileName().toString()).set(lastCount);
			processedCount.labels(fileName().toString()).set(success);
			return retry ? Optional.of(15) : Optional.empty();
		}
	}

	private static final Gauge processedCount = Gauge.build("shesmu_static_actions_processed_count",
			"The number of static actions defined in a file that were succsessfully added to the actions queue.")
			.labelNames("filename").register();

	private static final Gauge totalCount = Gauge
			.build("shesmu_static_actions_total_count", "The number of static actions defined in a file.")
			.labelNames("filename").register();

	private AutoUpdatingDirectory<StaticActionFile> configuration;
	private final Supplier<Stream<ActionDefinition>> definitions;
	private final Map<String, ActionRunner> runners = new HashMap<>();
	private final ActionConsumer sink;

	public StaticActions(ActionConsumer sink, Supplier<Stream<ActionDefinition>> definitions) {
		this.sink = sink;
		this.definitions = definitions;
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return Stream.of(new ConfigurationSection("Static Configuration") {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				configuration.stream()//
						.sorted(Comparator.comparing(StaticActionFile::fileName))//
						.forEach(config -> {
							renderer.line(config.fileName().toString(), config.lastCount);
						});
			}
		});
	}

	public void start() {
		if (configuration == null) {
			configuration = new AutoUpdatingDirectory<>(".actnow", StaticActionFile::new);
		}
	}
}
