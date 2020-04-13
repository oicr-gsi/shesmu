package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.HotloadingCompiler;
import ca.on.oicr.gsi.shesmu.server.InputSource;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.CallSiteRegistry;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;
import java.io.PrintStream;
import java.lang.invoke.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Compiles a user-specified file into a usable program and updates it as necessary */
public class CompiledGenerator implements DefinitionRepository {
  private final class Script implements WatchedFileListener {

    class ExportedConstantDefinition extends ConstantDefinition {

      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final MutableCallSite callsite;

      private final String invokeName;

      ExportedConstantDefinition(MethodHandle method, String name, Imyhat type) {
        super(name, type, "Constant from " + fileName, null);
        invokeName = String.format("%s %s", name, type.descriptor());
        callsite = FUNCTION_REGISTRY.upsert(new Pair<>(fileName.toString(), invokeName), method);
      }

      @Override
      protected void load(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(
            invokeName,
            Type.getMethodDescriptor(type().apply(TypeUtils.TO_ASM)),
            BSM,
            fileName.toString());
      }
    }

    class ExportedFunctionDefinition implements FunctionDefinition {

      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final MutableCallSite callsite;

      private final String invokeName;
      private final String name;
      private final Supplier<Stream<FunctionParameter>> parameters;
      private final Imyhat returnType;

      ExportedFunctionDefinition(
          MethodHandle method,
          String name,
          Imyhat returnType,
          Supplier<Stream<FunctionParameter>> parameters) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        invokeName =
            String.format(
                "%s %s %s",
                name,
                returnType().descriptor(),
                parameters.get().map(p -> p.type().descriptor()).collect(Collectors.joining(" ")));
        callsite = FUNCTION_REGISTRY.upsert(new Pair<>(fileName.toString(), invokeName), method);
      }

      @Override
      public String description() {
        return "Function from " + fileName;
      }

      @Override
      public Path filename() {
        return fileName;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return parameters.get();
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(
            invokeName,
            Type.getMethodDescriptor(
                returnType.apply(TypeUtils.TO_ASM),
                parameters.get().map(p -> p.type().apply(TypeUtils.TO_ASM)).toArray(Type[]::new)),
            BSM,
            fileName.toString());
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // Do nothing.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private MutableCallSite callsite;

    private FileTable dashboard;
    private List<String> errors = Collections.emptyList();
    private List<ExportedConstantDefinition> exportedConstants = Collections.emptyList();
    private List<ExportedFunctionDefinition> exportedFunctions = Collections.emptyList();
    private final Path fileName;
    private ActionGenerator generator = ActionGenerator.NULL;
    private volatile boolean live = true;
    private OliveRunInfo runInfo;
    private CompletableFuture<?> running = CompletableFuture.completedFuture(null);

    private Script(Path fileName) {
      this.fileName = fileName;
    }

    public Stream<ConstantDefinition> constants() {
      return exportedConstants.stream().map(x -> x);
    }

    public Stream<Pair<OliveRunInfo, FileTable>> dashboard() {
      return dashboard == null ? Stream.empty() : Stream.of(new Pair<>(runInfo, dashboard));
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

    public Stream<FunctionDefinition> functions() {
      return exportedFunctions.stream().map(x -> x);
    }

    public synchronized String run(OliveServices consumer, InputProvider input) {
      if (!live) {
        return "Deleted while waiting to run.";
      }
      try {
        generator.run(consumer, input);
        return "Completed normally";
      } catch (final Exception e) {
        e.printStackTrace();
        return e.toString();
      }
    }

    @Override
    public void start() {
      // Do nothing.
    }

    @Override
    public void stop() {
      generator.unregister();
      live = false;
    }

    @Override
    public synchronized Optional<Integer> update() {
      live = true;
      try (Timer timer = compileTime.labels(fileName.toString()).startTimer()) {
        final List<ExportedConstantDefinition> exportedConstants = new ArrayList<>();
        final List<ExportedFunctionDefinition> exportedFunctions = new ArrayList<>();
        final HotloadingCompiler compiler =
            new HotloadingCompiler(
                SOURCES::get,
                new DefinitionRepository() {
                  @Override
                  public Stream<ActionDefinition> actions() {
                    return definitionRepository.actions();
                  }

                  @Override
                  public Stream<ConstantDefinition> constants() {
                    return definitionRepository.constants();
                  }

                  @Override
                  public Stream<FunctionDefinition> functions() {
                    return definitionRepository
                        .functions()
                        .filter(f -> f.filename() == null || !f.filename().equals(fileName));
                  }

                  @Override
                  public Stream<ConfigurationSection> listConfiguration() {
                    return definitionRepository.listConfiguration();
                  }

                  @Override
                  public Stream<RefillerDefinition> refillers() {
                    return definitionRepository.refillers();
                  }

                  @Override
                  public Stream<SignatureDefinition> signatures() {
                    return definitionRepository.signatures();
                  }

                  @Override
                  public void writeJavaScriptRenderer(PrintStream writer) {
                    definitionRepository.writeJavaScriptRenderer(writer);
                  }
                });
        final Optional<ActionGenerator> result =
            compiler.compile(
                fileName,
                new LiveExportConsumer() {
                  @Override
                  public void constant(MethodHandle method, String name, Imyhat type) {
                    exportedConstants.add(new ExportedConstantDefinition(method, name, type));
                  }

                  @Override
                  public void function(
                      MethodHandle method,
                      String name,
                      Imyhat returnType,
                      Supplier<Stream<FunctionParameter>> parameters) {
                    exportedFunctions.add(
                        new ExportedFunctionDefinition(method, name, returnType, parameters));
                  }
                },
                ft -> dashboard = ft);
        sourceValid.labels(fileName.toString()).set(result.isPresent() ? 1 : 0);
        result.ifPresent(
            x -> {
              final Set<String> newNames =
                  Stream.concat(
                          exportedFunctions.stream().map(e -> e.invokeName),
                          exportedConstants.stream().map(e -> e.invokeName))
                      .collect(Collectors.toSet());
              callsite =
                  SCRIPT_REGISTRY.upsert(
                      fileName.toString(), MethodHandles.constant(ActionGenerator.class, x));
              this.exportedFunctions = exportedFunctions;
              this.exportedConstants = exportedConstants;
              // Find all callsites previously exported that no longer exists. Replace the method
              // handle in the call site with an exception.
              FUNCTION_REGISTRY
                  .streamSites()
                  .filter(
                      callSiteEntry ->
                          callSiteEntry.first().first().equals(fileName.toString())
                              && !newNames.contains(callSiteEntry.first().second()))
                  .forEach(
                      callSiteEntry ->
                          callSiteEntry
                              .second()
                              .setTarget(
                                  makeDeadMethodHandle(
                                      callSiteEntry.first().second(),
                                      callSiteEntry.second().type())));
              if (generator != x) {
                generator.unregister();
                x.register();
                generator = x;
              }
              runInfo = null;
            });
        errors = compiler.errors().collect(Collectors.toList());
        return result.isPresent() ? Optional.empty() : Optional.of(2);
      } catch (final Exception e) {
        e.printStackTrace();
        return Optional.of(2);
      }
    }
  }

  private static final Handle BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(CompiledGenerator.class),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);
  private static final MethodHandle CREATE_EXCEPTION;
  private static final CallSiteRegistry<Pair<String, String>> FUNCTION_REGISTRY =
      new CallSiteRegistry<>();
  public static final LatencyHistogram INPUT_FETCH_TIME =
      new LatencyHistogram(
          "shesmu_input_fetch_time", "The number of records for each input format.", "format");
  public static final Gauge INPUT_RECORDS =
      Gauge.build("shesmu_input_records", "The number of records for each input format.")
          .labelNames("format")
          .register();
  public static final Gauge OLIVE_WATCHDOG =
      Gauge.build(
              "shesmu_run_overtime",
              "Whether the input format or olive file failed to finish within deadline.")
          .labelNames("name")
          .register();
  private static final CallSiteRegistry<String> SCRIPT_REGISTRY = new CallSiteRegistry<>();
  public static final NameLoader<InputFormatDefinition> SOURCES =
      new NameLoader<>(AnnotatedInputFormatDefinition.formats(), InputFormatDefinition::name);
  private static final Gauge compileTime =
      Gauge.build(
              "shesmu_source_compile_time",
              "The number of seconds the last compilation took to perform.")
          .labelNames("filename")
          .register();
  private static final Gauge sourceValid =
      Gauge.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
          .labelNames("filename")
          .register();

  static {
    try {
      final MethodHandles.Lookup lookup = MethodHandles.lookup();
      CREATE_EXCEPTION =
          lookup.findConstructor(
              IllegalStateException.class, MethodType.methodType(void.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /** Bind to a exported function. */
  public static CallSite bootstrap(
      MethodHandles.Lookup lookup, String methodName, MethodType type, String fileName) {
    final CallSite result = FUNCTION_REGISTRY.get(new Pair<>(fileName, methodName));
    // There's a chance that this function was exported, another script compiled, then the function
    // was removed, and then the call site was initialised (since this happens lazily). If that was
    // the case, make an exception and return that instead.
    return (result != null)
        ? result
        : new ConstantCallSite(makeDeadMethodHandle(methodName.split(" ")[0], type));
  }

  public static boolean didFileTimeout(String fileName) {
    return OLIVE_WATCHDOG.labels(fileName).get() > 0;
  }

  private static MethodHandle makeDeadMethodHandle(String name, MethodType methodType) {
    // Create a method handle that instantiates a new exception
    final MethodHandle createException =
        CREATE_EXCEPTION.bindTo(String.format("Exported function %s is no longer valid.", name));

    // Now, create a method handle that appears to return the same type as the callsite expects and
    // throws the exception we just made
    final MethodHandle throwException =
        MethodHandles.foldArguments(
            MethodHandles.throwException(
                methodType.returnType(),
                createException.type().returnType().asSubclass(Throwable.class)),
            createException);
    // Now, create a method handle that takes the same arguments as the one the call site needs (but
    // just discards them) and throws the exception
    return MethodHandles.dropArguments(throwException, 0, methodType.parameterArray());
  }

  public static CallSite scriptCallsite(String fileName) {
    return SCRIPT_REGISTRY.get(fileName);
  }

  private final DefinitionRepository definitionRepository;
  private final ScheduledExecutorService executor;
  private Optional<AutoUpdatingDirectory<Script>> scripts = Optional.empty();
  private final ExecutorService workExecutor =
      Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

  public CompiledGenerator(
      ScheduledExecutorService executor, DefinitionRepository definitionRepository) {
    this.executor = executor;
    this.definitionRepository = DefinitionRepository.concat(definitionRepository, this);
  }

  @Override
  public Stream<ActionDefinition> actions() {
    return Stream.empty();
  }

  @Override
  public Stream<ConstantDefinition> constants() {
    return scripts().flatMap(Script::constants);
  }

  public Stream<Pair<OliveRunInfo, FileTable>> dashboard() {
    return scripts().flatMap(Script::dashboard);
  }

  /** Get all the error messages from the last compilation as an HTML blob. */
  public void errorHtml(SectionRenderer renderer) {
    scripts().forEach(script -> script.errorHtml(renderer));
  }

  @Override
  public Stream<FunctionDefinition> functions() {
    return scripts().flatMap(Script::functions);
  }

  @Override
  public Stream<ConfigurationSection> listConfiguration() {
    return Stream.empty();
  }

  @Override
  public Stream<RefillerDefinition> refillers() {
    return Stream.empty();
  }

  public void run(OliveServices consumer, InputSource input) {
    // Load all the input data in an attempt to cache it before any olives try to
    // use it. This avoids making the first olive seem really slow.
    final Set<String> usedFormats =
        scripts()
            .filter(s -> s.running.isDone())
            .flatMap(s -> s.generator.inputs())
            .collect(Collectors.toSet());
    // Allow inhibitions to be set on a per-input format format and skip fetching this data.
    final Set<String> inhibitedFormats =
        usedFormats
            .stream()
            .filter(consumer::isOverloaded)
            .collect(Collectors.toCollection(TreeSet::new));
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
                                  input.fetch(format.name(), false).collect(Collectors.toList());
                              INPUT_RECORDS.labels(format.name()).set(results.size());
                              return results;
                            } catch (final Exception e) {
                              e.printStackTrace();
                              // If we failed to load this format, pretend like it was inhibited and
                              // don't run dependent olives
                              inhibitedFormats.add(format.name());
                              return Collections.emptyList();
                            }
                          }));

          @Override
          public Stream<Object> fetch(String format) {
            return data.getOrDefault(format, Collections.emptyList()).stream();
          }
        };

    scripts()
        .filter(
            script ->
                script.running
                    .isDone()) // This is a potential race condition: an olive finished while we
        // were collecting the input. Since we check for the data the olive requires being
        // present, it's safe to run the olive even if it wasn't done when when previously checked.
        .filter(
            script ->
                script
                    .generator
                    .inputs()
                    .noneMatch(
                        inhibitedFormats
                            ::contains)) // Don't run any olives that require data we don't
        // have.
        .forEach(
            script -> {
              final AtomicReference<Runnable> inflight =
                  new AtomicReference<>(Server.inflight("Queued " + script.fileName.toString()));
              // For each script, create two futures: one that runs the olive script and
              // return true and one that will wait for the timeout and return false
              final CompletableFuture<OliveRunInfo> timeoutFuture = new CompletableFuture<>();
              final CompletableFuture<OliveRunInfo> processFuture =
                  CompletableFuture.supplyAsync(
                      () -> {
                        final Instant startTime = Instant.now();
                        inflight.get().run();
                        inflight.set(Server.inflight("Running " + script.fileName.toString()));
                        script.runInfo = new OliveRunInfo(true, "Running now", null, startTime);
                        final long inputCount =
                            script.dashboard == null
                                ? 0
                                : cache.fetch(script.dashboard.format().name()).count();
                        // We wait to schedule the timeout for when the script is actually
                        // starting
                        executor.schedule(
                            () ->
                                timeoutFuture.complete(
                                    new OliveRunInfo(
                                        false, "Deadline exceeded", inputCount, startTime)),
                            script.generator.timeout(),
                            TimeUnit.SECONDS);
                        return new OliveRunInfo(
                            true, script.run(consumer, cache), inputCount, startTime);
                      },
                      workExecutor);

              // Then create another future that waits for either of the above to finish and
              // nukes the other
              script.running =
                  CompletableFuture.anyOf(timeoutFuture, processFuture)
                      .thenAccept(
                          obj -> {
                            final OliveRunInfo runInfo = (OliveRunInfo) obj;
                            script.runInfo = runInfo;
                            OLIVE_WATCHDOG
                                .labels(script.fileName.toString())
                                .set(runInfo.isOk() ? 0 : 1);
                            timeoutFuture.cancel(true);
                            processFuture.cancel(true);
                            inflight.get().run();
                          });
            });
  }

  private Stream<Script> scripts() {
    return scripts.map(AutoUpdatingDirectory::stream).orElseGet(Stream::empty);
  }

  @Override
  public Stream<SignatureDefinition> signatures() {
    return Stream.empty();
  }

  public void start() {
    scripts = Optional.of(new AutoUpdatingDirectory<>(".shesmu", Script::new));
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {}
}
