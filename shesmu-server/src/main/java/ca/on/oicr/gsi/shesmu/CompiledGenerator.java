package ca.on.oicr.gsi.shesmu;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.util.server.HotloadingCompiler;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;

/**
 * Compiles a user-specified file into a usable program and updates it as
 * necessary
 */
public class CompiledGenerator extends ActionGenerator {

	private final class Script implements WatchedFileListener {
		private FileTable dashboard;
		private List<String> errors = Collections.emptyList();
		private final Path fileName;

		private ActionGenerator generator = ActionGenerator.NULL;

		private Script(Path fileName) {
			this.fileName = fileName;
		}

		public Stream<FileTable> dashboard() {
			return dashboard == null ? Stream.empty() : Stream.of(dashboard);
		}

		public void errorHtml(SectionRenderer renderer) {
			if (errors.isEmpty()) {
				return;
			}
			for (final String error : errors) {
				renderer.line(Stream.of(new Pair<>("class", "error")), "Compile Error",
						fileName.toString() + ":" + error);
			}
		}

		public synchronized <T> void run(ActionConsumer consumer, Function<Class<T>, Stream<T>> input) {
			try {
				generator.run(consumer, input);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void start() {
			update();
		}

		@Override
		public void stop() {
			generator.unregister();
		}

		@Override
		public synchronized Optional<Integer> update() {
			try (Timer timer = compileTime.labels(fileName.toString()).startTimer()) {
				final HotloadingCompiler compiler = new HotloadingCompiler(SOURCES::get,
						DefinitionRepository::allFunctions, DefinitionRepository::allActions,
						DefinitionRepository::allConstants);
				final Optional<ActionGenerator> result = compiler.compile(fileName, ft -> dashboard = ft);
				sourceValid.labels(fileName.toString()).set(result.isPresent() ? 1 : 0);
				result.ifPresent(x -> {
					if (generator != x) {
						generator.unregister();
						x.register();
						generator = x;
					}
				});
				errors = compiler.errors().collect(Collectors.toList());
				return result.isPresent() ? Optional.empty() : Optional.of(2);
			} catch (final Exception e) {
				e.printStackTrace();
				return Optional.of(2);
			}
		}
	}

	private static final Gauge compileTime = Gauge
			.build("shesmu_source_compile_time", "The number of seconds the last compilation took to perform.")
			.labelNames("filename").register();

	public static final Gauge INPUT_RECORDS = Gauge
			.build("shesmu_input_records", "The number of records for each input format.").labelNames("format")
			.register();

	private static final NameLoader<InputFormatDefinition> SOURCES = new NameLoader<>(InputFormatDefinition.formats(),
			InputFormatDefinition::name);

	private static final Gauge sourceValid = Gauge
			.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
			.labelNames("filename").register();

	private Optional<AutoUpdatingDirectory<Script>> scripts = Optional.empty();

	public CompiledGenerator() {
	}

	public Stream<FileTable> dashboard() {
		return scripts().flatMap(Script::dashboard);
	}

	/**
	 * Get all the error messages from the last compilation as an HTML blob.
	 *
	 * @return
	 */
	public void errorHtml(SectionRenderer renderer) {
		scripts().forEach(script -> script.errorHtml(renderer));
	}

	@Override
	public <T> void run(ActionConsumer consumer, Function<Class<T>, Stream<T>> input) {
		// Load all the input data in an attempt to cache it before any olives try to
		// use it. This avoids making the first olive seem really slow.
		InputFormatDefinition.formats().forEach(format -> {
			try {
				INPUT_RECORDS.labels(format.name()).set(format.input(format.itemClass()).count());
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		scripts().forEach(script -> script.run(consumer, input));
	}

	private Stream<Script> scripts() {
		return scripts.map(AutoUpdatingDirectory::stream).orElseGet(Stream::empty);
	}

	public void start() {
		scripts = Optional.of(new AutoUpdatingDirectory<>(".shesmu", Script::new));
	}

}
