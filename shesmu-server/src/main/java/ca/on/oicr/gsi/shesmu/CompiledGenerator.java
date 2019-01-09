package ca.on.oicr.gsi.shesmu;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
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
public class CompiledGenerator {

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

		public synchronized void run(ActionConsumer consumer, InputProvider input) {
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
	public static final LatencyHistogram INPUT_FETCH_TIME = new LatencyHistogram("shesmu_input_fetch_time",
			"The number of records for each input format.", "format");
	public static final Gauge OLIVE_WATCHDOG = Gauge
			.build("shesmu_run_overtime", "Whether the input format or olive file failed to finish within deadline.")
			.labelNames("name").register();
	private static final NameLoader<InputFormatDefinition> SOURCES = new NameLoader<>(InputFormatDefinition.formats(),
			InputFormatDefinition::name);

	private static final Gauge sourceValid = Gauge
			.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
			.labelNames("filename").register();

	public static boolean didFileTimeout(String fileName) {
		return OLIVE_WATCHDOG.labels(fileName).get() > 0;
	}

	private final ScheduledExecutorService executor;

	private Optional<AutoUpdatingDirectory<Script>> scripts = Optional.empty();

	public CompiledGenerator(ScheduledExecutorService executor) {
		this.executor = executor;
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

	public void run(ActionConsumer consumer, InputProvider input) {
		// Load all the input data in an attempt to cache it before any olives try to
		// use it. This avoids making the first olive seem really slow.
		// TODO: collect only the formats that olives are currently using
		final InputProvider cache = new InputProvider() {
			final Map<Class<?>, List<?>> data = InputFormatDefinition.formats()//
					.collect(Collectors.toMap(InputFormatDefinition::itemClass, format -> {
						try (AutoCloseable timer = INPUT_FETCH_TIME.start(format.name());
								AutoCloseable inflight = Server.inflightCloseable("Fetching " + format.name())) {
							final List<Object> results = format.input(format.itemClass()).collect(Collectors.toList());
							INPUT_RECORDS.labels(format.name()).set(results.size());
							return results;
						} catch (final Exception e) {
							e.printStackTrace();
							return Collections.emptyList();
						}
					}));

			@Override
			public <X> Stream<X> fetch(Class<X> format) {
				return data.getOrDefault(format, Collections.emptyList()).stream().map(format::cast);
			}
		};

		final List<CompletableFuture<?>> futures = scripts()//
				.map(script -> {
					final Runnable inflight = Server.inflight(script.fileName.toString());
					// For each script, create two futures: one that runs the olive script and
					// return true and one that will wait for the timeout and return false
					final CompletableFuture<Boolean> timeoutFuture = new CompletableFuture<>();
					final CompletableFuture<Boolean> processFuture = CompletableFuture.supplyAsync(() -> {
						// We wait to schedule the timeout for when the script is actually starting
						executor.schedule(() -> timeoutFuture.complete(false), script.generator.timeout(),
								TimeUnit.SECONDS);

						script.run(consumer, cache);
						return true;
					}, executor);

					// Then create another future that waits for either of the above to finish and
					// nukes the other
					return CompletableFuture.anyOf(timeoutFuture, processFuture).thenAccept(obj -> {
						final boolean ok = (Boolean) obj;
						OLIVE_WATCHDOG.labels(script.fileName.toString()).set(ok ? 0 : 1);
						(ok ? timeoutFuture : processFuture).cancel(true);
						inflight.run();
					});
				}) //
				.collect(Collectors.toList());
		// Now wait for all of those tasks to finish
		for (CompletableFuture<?> future : futures) {
			future.join();
		}
	}

	private Stream<Script> scripts() {
		return scripts.map(AutoUpdatingDirectory::stream).orElseGet(Stream::empty);
	}

	public void start() {
		scripts = Optional.of(new AutoUpdatingDirectory<>(".shesmu", Script::new));
	}

}
