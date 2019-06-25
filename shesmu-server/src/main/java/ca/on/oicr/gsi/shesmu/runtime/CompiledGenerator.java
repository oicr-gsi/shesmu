package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.server.HotloadingCompiler;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Compiles a user-specified file into a usable program and updates it as necessary */
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
        renderer.line(
            Stream.of(new Pair<>("class", "error")),
            "Compile Error",
            fileName.toString() + ":" + error);
      }
    }

    public synchronized void run(OliveServices consumer, InputProvider input) {
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
        final HotloadingCompiler compiler =
            new HotloadingCompiler(SOURCES::get, definitionRepository);
        final Optional<ActionGenerator> result = compiler.compile(fileName, ft -> dashboard = ft);
        sourceValid.labels(fileName.toString()).set(result.isPresent() ? 1 : 0);
        result.ifPresent(
            x -> {
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

  private static final Gauge compileTime =
      Gauge.build(
              "shesmu_source_compile_time",
              "The number of seconds the last compilation took to perform.")
          .labelNames("filename")
          .register();

  public static final Gauge INPUT_RECORDS =
      Gauge.build("shesmu_input_records", "The number of records for each input format.")
          .labelNames("format")
          .register();
  public static final LatencyHistogram INPUT_FETCH_TIME =
      new LatencyHistogram(
          "shesmu_input_fetch_time", "The number of records for each input format.", "format");
  public static final Gauge OLIVE_WATCHDOG =
      Gauge.build(
              "shesmu_run_overtime",
              "Whether the input format or olive file failed to finish within deadline.")
          .labelNames("name")
          .register();
  public static final NameLoader<InputFormatDefinition> SOURCES =
      new NameLoader<>(AnnotatedInputFormatDefinition.formats(), InputFormatDefinition::name);

  private static final Gauge sourceValid =
      Gauge.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
          .labelNames("filename")
          .register();

  public static boolean didFileTimeout(String fileName) {
    return OLIVE_WATCHDOG.labels(fileName).get() > 0;
  }

  private final ScheduledExecutorService executor;

  private final ExecutorService workExecutor =
      Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

  private Optional<AutoUpdatingDirectory<Script>> scripts = Optional.empty();
  private final DefinitionRepository definitionRepository;

  public CompiledGenerator(
      ScheduledExecutorService executor, DefinitionRepository definitionRepository) {
    this.executor = executor;
    this.definitionRepository = definitionRepository;
  }

  public Stream<FileTable> dashboard() {
    return scripts().flatMap(Script::dashboard);
  }

  /** Get all the error messages from the last compilation as an HTML blob. */
  public void errorHtml(SectionRenderer renderer) {
    scripts().forEach(script -> script.errorHtml(renderer));
  }

  public void run(OliveServices consumer, InputProvider input) {
    // Load all the input data in an attempt to cache it before any olives try to
    // use it. This avoids making the first olive seem really slow.
    final Set<String> usedFormats =
        scripts().flatMap(s -> s.generator.inputs()).collect(Collectors.toSet());
    // Allow inhibitions to be set on a per-input format format and skip fetching this data.
    final Set<String> inhibitedFormats =
        usedFormats.stream().filter(consumer::isOverloaded).collect(Collectors.toSet());
    final InputProvider cache =
        new InputProvider() {
          final Map<String, List<Object>> data =
              SOURCES
                  .all()
                  .filter(
                      format ->
                          usedFormats.contains(format.name())
                              && !inhibitedFormats.contains(format.name()))
                  .collect(
                      Collectors.toMap(
                          InputFormatDefinition::name,
                          format -> {
                            try (AutoCloseable timer = INPUT_FETCH_TIME.start(format.name());
                                AutoCloseable inflight =
                                    Server.inflightCloseable("Fetching " + format.name())) {
                              final List<Object> results =
                                  input.fetch(format.name()).collect(Collectors.toList());
                              INPUT_RECORDS.labels(format.name()).set(results.size());
                              return results;
                            } catch (final Exception e) {
                              e.printStackTrace();
                              return Collections.emptyList();
                            }
                          }));

          @Override
          public Stream<Object> fetch(String format) {
            return data.getOrDefault(format, Collections.emptyList()).stream();
          }
        };

    final List<CompletableFuture<?>> futures =
        scripts()
            .filter(
                script ->
                    script
                        .generator
                        .inputs()
                        .noneMatch(
                            inhibitedFormats
                                ::contains)) // Don't run any olives that require data we don't
            // have.
            .map(
                script -> {
                  final Runnable inflight = Server.inflight(script.fileName.toString());
                  // For each script, create two futures: one that runs the olive script and
                  // return true and one that will wait for the timeout and return false
                  final CompletableFuture<Boolean> timeoutFuture = new CompletableFuture<>();
                  final CompletableFuture<Boolean> processFuture =
                      CompletableFuture.supplyAsync(
                          () -> {
                            // We wait to schedule the timeout for when the script is actually
                            // starting
                            executor.schedule(
                                () -> timeoutFuture.complete(false),
                                script.generator.timeout(),
                                TimeUnit.SECONDS);

                            script.run(consumer, cache);
                            return true;
                          },
                          workExecutor);

                  // Then create another future that waits for either of the above to finish and
                  // nukes the other
                  return CompletableFuture.anyOf(timeoutFuture, processFuture)
                      .thenAccept(
                          obj -> {
                            final boolean ok = (Boolean) obj;
                            OLIVE_WATCHDOG.labels(script.fileName.toString()).set(ok ? 0 : 1);
                            (ok ? timeoutFuture : processFuture).cancel(true);
                            inflight.run();
                          });
                })
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
