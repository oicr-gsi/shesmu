package ca.on.oicr.gsi.shesmu.runtime;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer.DefineVariableExport;
import ca.on.oicr.gsi.shesmu.compiler.OliveCompilerServices;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.SignableVariableCheck;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.HotloadingCompiler;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier;
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
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Compiles a user-specified file into a usable program and updates it as necessary */
public class CompiledGenerator implements DefinitionRepository {

  public static final class DefinitionKey {
    private final String definitionName;
    private final String inputFormat;
    private final String variableName;

    public DefinitionKey(
        String inputFormat, String definitionName, String variableName, String[] otherVariables) {
      this.inputFormat = inputFormat;
      // We need to have a consistent name for this definition. Suppose the export changes to drop
      // or rename a variable that we need; it must be that we don't upgrade that variable, but we
      // also need to not upgrade the other definition call and all the related variables.
      // Therefore, the name of a definition must include the names of all the output variables (and
      // their types).
      this.definitionName =
          definitionName
              + Stream.concat(Stream.of(variableName), Stream.of(otherVariables))
                  .sorted()
                  .distinct()
                  .collect(Collectors.joining(" ", " ", ""));
      this.variableName = variableName;
    }

    public DefinitionKey(String inputFormat, String definitionName, String variableName) {
      this.inputFormat = inputFormat;
      this.definitionName = definitionName;
      this.variableName = variableName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DefinitionKey that = (DefinitionKey) o;
      return inputFormat.equals(that.inputFormat)
          && definitionName.equals(that.definitionName)
          && variableName.equals(that.variableName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(inputFormat, definitionName, variableName);
    }
  }

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
      public void load(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(
            invokeName, Type.getMethodDescriptor(type().apply(TO_ASM)), BSM, fileName.toString());
      }
    }

    class ExportedDefineOliveDefinition implements Supplier<CallableOliveDefinition> {
      @SuppressWarnings("FieldCanBeLocal")
      private final List<MutableCallSite> callsites = new ArrayList<>();

      private final List<DefineVariableExport> checks;
      private final String inputFormatName;
      private final boolean isRoot;
      private final String name;
      private final List<Imyhat> parameterTypes;
      private final String qualifiedName;
      private final List<DefineVariableExport> variables;

      public ExportedDefineOliveDefinition(
          MethodHandle method,
          String name,
          String inputFormatName,
          boolean isRoot,
          List<Imyhat> parameterTypes,
          List<DefineVariableExport> variables,
          List<DefineVariableExport> checks) {
        this.name = name;
        this.inputFormatName = inputFormatName;
        this.isRoot = isRoot;
        this.parameterTypes = parameterTypes;
        this.checks = checks;

        this.variables = variables;

        qualifiedName =
            parameterTypes
                .stream()
                .map(Imyhat::descriptor)
                .collect(Collectors.joining(" ", name + " ", ""));
        for (final List<DefineVariableExport> variableSubset : powerSet(variables)) {
          if (variableSubset.isEmpty()) {
            continue;
          }
          final String expandedName =
              qualifiedName
                  + variableSubset
                      .stream()
                      .sorted(Comparator.comparing(DefineVariableExport::name))
                      .map(d -> d.name() + "$" + d.type().descriptor())
                      .collect(Collectors.joining(" ", " ", ""));
          callsites.add(DEFINE_REGISTRY.upsert(new Pair<>(inputFormatName, expandedName), method));
          for (final DefineVariableExport variable : variableSubset) {
            callsites.add(
                DEFINE_VARIABLE_REGISTRY.upsert(
                    new DefinitionKey(
                        inputFormatName,
                        expandedName,
                        variable.name() + "$" + variable.type().descriptor()),
                    variable.method()));
          }
          for (final DefineVariableExport variable : checks) {
            callsites.add(
                DEFINE_SIGNATURE_CHECK_REGISTRY.upsert(
                    new DefinitionKey(inputFormatName, expandedName, variable.name()),
                    variable.method()));
          }
        }
        MutableCallSite.syncAll(callsites.stream().toArray(MutableCallSite[]::new));
      }

      @Override
      public CallableOliveDefinition get() {
        // We create a new one of these whenever necessary because the used information needs to be
        // isolated between users
        return new CallableOliveDefinition() {
          private final Set<String> used = new TreeSet<>();
          private final List<SignableVariableCheck> checkers =
              checks
                  .stream()
                  .map(
                      c ->
                          new SignableVariableCheck() {
                            @Override
                            public String name() {
                              return c.name();
                            }

                            @Override
                            public void render(GeneratorAdapter methodGen) {
                              methodGen.invokeDynamic(
                                  c.name(),
                                  Type.getMethodDescriptor(Type.BOOLEAN_TYPE, A_OBJECT_TYPE),
                                  BSM_DEFINE_SIGNATURE_CHECK,
                                  Stream.concat(
                                          Stream.of(inputFormatName, qualifiedName), used.stream())
                                      .toArray());
                            }
                          })
                  .collect(Collectors.toList());
          private final List<Target> targets =
              variables
                  .stream()
                  .map(
                      v ->
                          new InputVariable() {
                            @Override
                            public void extract(GeneratorAdapter methodGen) {
                              methodGen.invokeDynamic(
                                  v.name() + "$" + v.type().descriptor(),
                                  Type.getMethodDescriptor(v.type().apply(TO_ASM), A_OBJECT_TYPE),
                                  BSM_DEFINE_VARIABLE,
                                  Stream.concat(
                                          Stream.of(inputFormatName, qualifiedName), used.stream())
                                      .toArray());
                            }

                            @Override
                            public Flavour flavour() {
                              return v.flavour();
                            }

                            @Override
                            public String name() {
                              return v.name();
                            }

                            @Override
                            public void read() {
                              // We only want to include the values that were used in the signature.
                              used.add(v.name() + "$" + v.type().descriptor());
                            }

                            @Override
                            public Imyhat type() {
                              return v.type();
                            }
                          })
                  .collect(Collectors.toList());

          @Override
          public void collectSignables(
              Set<String> signableNames, Consumer<SignableVariableCheck> addSignableCheck) {
            checkers.forEach(addSignableCheck);
          }

          @Override
          public Type currentType() {
            return A_OBJECT_TYPE;
          }

          @Override
          public Stream<OliveClauseRow> dashboardInner(
              Optional<String> label, int line, int column) {
            return Stream.of(
                new OliveClauseRow(
                    label.orElse(name),
                    line,
                    column,
                    true,
                    !isRoot,
                    targets
                        .stream()
                        .map(
                            x ->
                                new VariableInformation(
                                    x.name(), x.type(), Stream.empty(), Behaviour.DEFINITION))));
          }

          @Override
          public Path filename() {
            return fileName;
          }

          @Override
          public String format() {
            return inputFormatName;
          }

          @Override
          public void generateCall(GeneratorAdapter methodGen) {
            methodGen.invokeDynamic(
                qualifiedName,
                Type.getMethodDescriptor(
                    A_STREAM_TYPE,
                    Stream.concat(
                            Stream.of(
                                A_STREAM_TYPE,
                                A_OLIVE_SERVICES_TYPE,
                                A_INPUT_PROVIDER_TYPE,
                                A_OPTIONAL_TYPE,
                                A_STRING_TYPE,
                                Type.INT_TYPE,
                                Type.INT_TYPE,
                                A_STRING_TYPE,
                                A_SIGNATURE_ACCESSOR_TYPE),
                            parameterTypes.stream().map(t -> t.apply(TO_ASM)))
                        .toArray(Type[]::new)),
                BSM_DEFINE,
                Stream.concat(Stream.of(inputFormatName), used.stream()).toArray());
          }

          @Override
          public void generatePreamble(GeneratorAdapter methodGen) {
            // No preamble
          }

          @Override
          public boolean isRoot() {
            return isRoot;
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public Optional<Stream<Target>> outputStreamVariables(
              OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
            return Optional.of(targets.stream());
          }

          @Override
          public Type parameter(int i) {
            return parameterTypes.get(i).apply(TO_ASM);
          }

          @Override
          public int parameterCount() {
            return parameterTypes.size();
          }

          @Override
          public Imyhat parameterType(int index) {
            return parameterTypes.get(index);
          }

          @Override
          public int parameters() {
            return parameterTypes.size();
          }
        };
      }

      private List<List<DefineVariableExport>> powerSet(List<DefineVariableExport> variables) {
        Stream<Supplier<Stream<DefineVariableExport>>> supplier = Stream.of(Stream::empty);
        for (final DefineVariableExport variable : variables) {
          supplier =
              supplier.flatMap(
                  head -> Stream.of(head, () -> Stream.concat(head.get(), Stream.of(variable))));
        }
        return supplier.map(s -> s.get().collect(Collectors.toList())).collect(Collectors.toList());
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
                returnType.apply(TO_ASM),
                parameters.get().map(p -> p.type().apply(TO_ASM)).toArray(Type[]::new)),
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
    private List<ExportedDefineOliveDefinition> exportedDefineOlives = Collections.emptyList();
    private List<ExportedFunctionDefinition> exportedFunctions = Collections.emptyList();
    private final Path fileName;
    private final DefinitionRepository allowedDefinitions =
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
          public Stream<CallableOliveDefinition> oliveDefinitions() {
            return definitionRepository
                .oliveDefinitions()
                .filter(od -> !od.filename().equals(fileName));
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
        };
    private ActionGenerator generator = ActionGenerator.NULL;
    private Set<ImportVerifier> imports = Collections.emptySet();
    private final String instance;
    private volatile boolean live = true;
    private OliveRunInfo runInfo;
    private CompletableFuture<?> running = CompletableFuture.completedFuture(null);

    private Script(Path fileName) {
      this.fileName = fileName;
      instance =
          RuntimeSupport.removeExtension(fileName, ".shesmu").replaceAll("[^a-zA-Z0-9_]", "_");
    }

    public void checkDefinitions() {
      // Since the definitions can change, check if any of the imports used by this script change
      // and trigger a rebuild.
      if (!imports.stream().allMatch(i -> i.stillMatches(allowedDefinitions))) {
        FileWatcher.DATA_DIRECTORY.trigger(this.fileName);
      }
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

    public Stream<CallableOliveDefinition> oliveDefinitions() {
      return exportedDefineOlives.stream().map(Supplier::get);
    }

    public synchronized String run(OliveServices consumer, InputProvider input) {
      if (!live) {
        return "Deleted while waiting to run.";
      }
      if (CompiledGenerator.this.checkPaused.test(fileName.toString())) {
        return "Script is paused.";
      }
      try (final MonitoredOliveServices monitoredConsumer =
          new MonitoredOliveServices(consumer, fileName.toString())) {
        generator.run(monitoredConsumer, input);
        return "Completed normally";
      } catch (final Throwable e) {
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
        final List<ExportedDefineOliveDefinition> exportedDefineOlives = new ArrayList<>();
        final List<ExportedConstantDefinition> exportedConstants = new ArrayList<>();
        final List<ExportedFunctionDefinition> exportedFunctions = new ArrayList<>();
        final Set<ImportVerifier> imports = new HashSet<>();
        final HotloadingCompiler compiler =
            new HotloadingCompiler(SOURCES::get, allowedDefinitions);
        final Optional<ActionGenerator> result =
            compiler.compile(
                fileName,
                new LiveExportConsumer() {
                  @Override
                  public void constant(MethodHandle method, String name, Imyhat type) {
                    exportedConstants.add(
                        new ExportedConstantDefinition(
                            method,
                            String.join(Parser.NAMESPACE_SEPARATOR, "olive", instance, name),
                            type));
                  }

                  @Override
                  public void defineOlive(
                      MethodHandle method,
                      String name,
                      String inputFormatName,
                      boolean isRoot,
                      List<Imyhat> parameterTypes,
                      List<DefineVariableExport> variables,
                      List<DefineVariableExport> checks) {
                    exportedDefineOlives.add(
                        new ExportedDefineOliveDefinition(
                            method,
                            String.join(Parser.NAMESPACE_SEPARATOR, "olive", instance, name),
                            inputFormatName,
                            isRoot,
                            parameterTypes,
                            variables,
                            checks));
                  }

                  @Override
                  public void function(
                      MethodHandle method,
                      String name,
                      Imyhat returnType,
                      Supplier<Stream<FunctionParameter>> parameters) {
                    exportedFunctions.add(
                        new ExportedFunctionDefinition(
                            method,
                            String.join(Parser.NAMESPACE_SEPARATOR, "olive", instance, name),
                            returnType,
                            parameters));
                  }
                },
                ft -> dashboard = ft,
                imports::add);
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
              this.exportedDefineOlives = exportedDefineOlives;
              this.imports = imports;
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

  public static final Type A_CALLSITE_TYPE = Type.getType(CallSite.class);
  private static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  public static final Type A_LOOKUP_TYPE = Type.getType(Lookup.class);
  public static final Type A_METHOD_TYPE_TYPE = Type.getType(MethodType.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OLIVE_SERVICES_TYPE = Type.getType(OliveServices.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_SIGNATURE_ACCESSOR_TYPE = Type.getType(SignatureAccessor.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  public static final Type A_STRING_TYPE = Type.getType(String.class);
  public static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  public static final Handle BSM_DEFINE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(CompiledGenerator.class),
          "bootstrapDefine",
          Type.getMethodDescriptor(
              A_CALLSITE_TYPE,
              A_LOOKUP_TYPE,
              A_STRING_TYPE,
              A_METHOD_TYPE_TYPE,
              A_STRING_TYPE,
              A_STRING_ARRAY_TYPE),
          false);
  public static final Handle BSM_DEFINE_VARIABLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(CompiledGenerator.class),
          "bootstrapDefineVariable",
          Type.getMethodDescriptor(
              A_CALLSITE_TYPE,
              A_LOOKUP_TYPE,
              A_STRING_TYPE,
              A_METHOD_TYPE_TYPE,
              A_STRING_TYPE,
              A_STRING_TYPE,
              A_STRING_ARRAY_TYPE),
          false);
  public static final Handle BSM_DEFINE_SIGNATURE_CHECK =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(CompiledGenerator.class),
          "bootstrapDefineSignatureCheck",
          Type.getMethodDescriptor(
              A_CALLSITE_TYPE,
              A_LOOKUP_TYPE,
              A_STRING_TYPE,
              A_METHOD_TYPE_TYPE,
              A_STRING_TYPE,
              A_STRING_TYPE,
              A_STRING_ARRAY_TYPE),
          false);
  private static final Handle BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(CompiledGenerator.class),
          "bootstrap",
          Type.getMethodDescriptor(
              A_CALLSITE_TYPE, A_LOOKUP_TYPE, A_STRING_TYPE, A_METHOD_TYPE_TYPE, A_STRING_TYPE),
          false);

  private static final MethodHandle CREATE_EXCEPTION;
  private static final CallSiteRegistry<Pair<String, String>> DEFINE_REGISTRY =
      new CallSiteRegistry<>();
  private static final CallSiteRegistry<DefinitionKey> DEFINE_SIGNATURE_CHECK_REGISTRY =
      new CallSiteRegistry<>();
  private static final CallSiteRegistry<DefinitionKey> DEFINE_VARIABLE_REGISTRY =
      new CallSiteRegistry<>();
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

  public static CallSite bootstrapDefine(
      MethodHandles.Lookup lookup,
      String defineName,
      MethodType type,
      String inputFormat,
      String... otherVariables) {
    final CallSite result =
        DEFINE_REGISTRY.get(
            new Pair<>(
                inputFormat,
                defineName
                    + Stream.of(otherVariables)
                        .sorted()
                        .collect(Collectors.joining(" ", " ", ""))));
    return (result != null)
        ? result
        : new ConstantCallSite(
            makeDeadMethodHandle(
                String.format("Definition %s on %s", defineName, inputFormat), type));
  }

  public static CallSite bootstrapDefineSignatureCheck(
      MethodHandles.Lookup lookup,
      String variableName,
      MethodType type,
      String inputFormat,
      String defineName,
      String... otherVariables) {
    final CallSite result =
        DEFINE_SIGNATURE_CHECK_REGISTRY.get(
            new DefinitionKey(inputFormat, defineName, variableName, otherVariables));
    return (result != null)
        ? result
        : new ConstantCallSite(
            makeDeadMethodHandle(
                String.format(
                    "Signature check for %s from %s on %s", variableName, defineName, inputFormat),
                type));
  }

  public static CallSite bootstrapDefineVariable(
      MethodHandles.Lookup lookup,
      String variableName,
      MethodType type,
      String inputFormat,
      String defineName,
      String... otherVariables) {
    final CallSite result =
        DEFINE_VARIABLE_REGISTRY.get(
            new DefinitionKey(inputFormat, defineName, variableName, otherVariables));
    return (result != null)
        ? result
        : new ConstantCallSite(
            makeDeadMethodHandle(
                String.format("Variable %s from %s on %s", variableName, defineName, inputFormat),
                type));
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

  private final Predicate<String> checkPaused;
  private final DefinitionRepository definitionRepository;
  private final ScheduledExecutorService executor;
  private Optional<AutoUpdatingDirectory<Script>> scripts = Optional.empty();
  private final ExecutorService workExecutor =
      Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

  public CompiledGenerator(
      ScheduledExecutorService executor,
      DefinitionRepository definitionRepository,
      Predicate<String> checkPaused) {
    this.executor = executor;
    this.definitionRepository = DefinitionRepository.concat(definitionRepository, this);
    this.checkPaused = checkPaused;
    executor.scheduleWithFixedDelay(
        () ->
            scripts
                .map(AutoUpdatingDirectory::stream)
                .orElseGet(Stream::empty)
                .forEach(Script::checkDefinitions),
        10,
        5,
        TimeUnit.MINUTES);
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
  public Stream<CallableOliveDefinition> oliveDefinitions() {
    return scripts().flatMap(Script::oliveDefinitions);
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
    // Allow inhibitions to be set on a per-input format and skip fetching this data.
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

  public void start(FileWatcher fileWatcher) {
    scripts = Optional.of(new AutoUpdatingDirectory<>(fileWatcher, ".shesmu", Script::new));
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {}
}
