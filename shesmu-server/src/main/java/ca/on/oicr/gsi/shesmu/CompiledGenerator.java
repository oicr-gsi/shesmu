package ca.on.oicr.gsi.shesmu;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;

/**
 * Compiles a user-specified file into a usable program and updates it as
 * necessary
 */
public class CompiledGenerator extends ActionGenerator {

	private final class Script implements WatchedFileListener {
		private String errors = "Not yet compiled or exception during compilation.";
		private final Path fileName;

		private ActionGenerator generator = ActionGenerator.NULL;

		private Script(Path fileName) {
			this.fileName = fileName;
		}

		@Override
		public void start() {
			scripts.put(fileName, this);
			update();
		}

		@Override
		public void stop() {
			scripts.remove(fileName);
		}

		@Override
		public Optional<Integer> update() {
			try (Timer timer = compileTime.labels(fileName.toString()).startTimer()) {
				final HotloadingCompiler compiler = new HotloadingCompiler(SOURCES::get, functions, actions, constants);
				final Optional<ActionGenerator> result = compiler.compile(fileName);
				sourceValid.labels(fileName.toString()).set(result.isPresent() ? 1 : 0);
				result.ifPresent(x -> {
					if (generator != x) {
						generator.unregister();
						x.register();
						generator = x;
					}
				});
				errors = compiler.errors().collect(Collectors.joining("<br/>"));
				return result.isPresent() ? Optional.empty() : Optional.of(2);
			}
		}
	}

	private static final Gauge compileTime = Gauge
			.build("shesmu_source_compile_time", "The number of seconds the last compilation took to perform.")
			.labelNames("filename").register();

	private static final NameLoader<InputFormatDefinition> SOURCES = new NameLoader<>(InputFormatDefinition.formats(),
			InputFormatDefinition::name);

	private static final Gauge sourceValid = Gauge
			.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
			.labelNames("filename").register();
	private final Supplier<Stream<ActionDefinition>> actions;

	private final Supplier<Stream<Constant>> constants;

	private final Supplier<Stream<FunctionDefinition>> functions;

	private final Map<Path, Script> scripts = new ConcurrentHashMap<>();

	public CompiledGenerator(Supplier<Stream<FunctionDefinition>> functions, Supplier<Stream<ActionDefinition>> actions,
			Supplier<Stream<Constant>> constants) {
		this.functions = functions;
		this.actions = actions;
		this.constants = constants;
	}

	/**
	 * Get all the error messages from the last compilation as an HTML blob.
	 *
	 * @return
	 */
	public String errorHtml() {
		final StringBuilder builder = new StringBuilder();
		for (final Script script : scripts.values()) {
			if (script.errors.length() > 0) {
				builder.append("<p><b>").append(script.fileName.toString()).append("</b></p><p>").append(script.errors)
						.append("</p>");
			}
		}
		return builder.toString();
	}

	@Override
	public <T> void run(Consumer<Action> consumer, Function<Class<T>, Stream<T>> input) {
		scripts.values().forEach(script -> script.generator.run(consumer, input));
	}

	public void start() {
		FileWatcher.DATA_DIRECTORY.register(".shesmu", Script::new);
	}

}
