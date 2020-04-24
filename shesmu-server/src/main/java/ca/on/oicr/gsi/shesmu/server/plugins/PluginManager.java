package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureVariableForDynamicSigner;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureVariableForStaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.ExportSearch;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.input.JsonInputSource;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuJsonInputSource;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.refill.ShesmuRefill;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.ShesmuSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.StaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.InputSource;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.status.TableRowWriter;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * A source of actions, constants, and functions based on configuration files where every
 * configuration file can create plugins using annotations on methods.
 */
public final class PluginManager
    implements DefinitionRepository, InputSource, SourceLocationLinker {
  private interface Binder<D> {
    D bind(String name, Path path);
  }

  private interface DynamicInvoker {
    void write(GeneratorAdapter methodGen, String pathToInstance);
  }

  private class FormatTypeWrapper<F extends PluginFileType<T>, T extends PluginFile> {
    private final class ArbitraryActionDefintition extends ActionDefinition {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final CallSite callsite;

      private final String fixedName;

      private ArbitraryActionDefintition(
          String name,
          MethodHandle handle,
          String description,
          Path filename,
          Stream<ActionParameterDefinition> parameters) {
        super(name, description, filename, parameters);
        fixedName = name + " action";
        callsite = installArbitrary(fixedName, handle.asType(MethodType.methodType(Action.class)));
      }

      @Override
      public void initialize(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(
            fixedName, Type.getMethodDescriptor(Type.getType(Action.class)), BSM_HANDLE_ARBITRARY);
      }
    }

    private final class ArbitraryConstantDefinition extends ConstantDefinition {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final CallSite callsite;

      private final String fixedName;

      public ArbitraryConstantDefinition(
          String name, MethodHandle target, Imyhat returnType, String description, Path path) {
        super(name, returnType, description, path);
        final MethodHandle handle = target.asType(MethodType.methodType(returnType.javaType()));

        fixedName = name + " " + returnType.descriptor();
        callsite = installArbitrary(fixedName, handle);
      }

      @Override
      protected void load(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(
            fixedName,
            Type.getMethodDescriptor(type().apply(TypeUtils.TO_ASM)),
            BSM_HANDLE_ARBITRARY);
      }
    }

    private final class ArbitraryDynamicSignatureDefintion
        extends SignatureVariableForDynamicSigner {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final CallSite callsite;

      private final Path fileName;

      private final String fixedName;

      public ArbitraryDynamicSignatureDefintion(
          String name, MethodHandle handle, Imyhat type, Path fileName) {
        super(name, type);
        fixedName = name + " static signer";
        callsite = installArbitrary(fixedName, handle);
        this.fileName = fileName;
      }

      @Override
      public Path filename() {
        return fileName;
      }

      @Override
      protected void newInstance(GeneratorAdapter method) {
        method.invokeDynamic(
            fixedName,
            Type.getMethodDescriptor(Type.getType(DynamicSigner.class)),
            BSM_HANDLE_ARBITRARY);
      }
    }

    public final class ArbitraryFunctionDefinition implements FunctionDefinition {

      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final CallSite callsite;

      private final String description;
      private final String descriptor;
      private final String fixedName;
      private final String name;
      private final FunctionParameter[] parameters;
      private final Path path;
      private final Imyhat returnType;

      public ArbitraryFunctionDefinition(
          String name,
          String description,
          Path path,
          MethodHandle handle,
          Imyhat returnType,
          FunctionParameter... parameters) {
        this.name = name;
        this.description = description;
        this.path = path;
        this.returnType = returnType;
        this.parameters = parameters;

        fixedName =
            Stream.of(parameters)
                .map(p -> p.type().descriptor())
                .collect(Collectors.joining(" ", name + " ", " " + returnType.descriptor()));
        descriptor =
            Type.getMethodDescriptor(
                returnType.apply(TypeUtils.TO_ASM),
                Stream.of(parameters)
                    .map(p -> p.type().apply(TypeUtils.TO_ASM))
                    .toArray(Type[]::new));
        callsite = installArbitrary(fixedName, handle);
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public Path filename() {
        return path;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.of(parameters);
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        methodGen.invokeDynamic(fixedName, descriptor, BSM_HANDLE_ARBITRARY);
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // Nothing to do.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    }

    private final class ArbitraryRefillerDefinition implements RefillerDefinition {
      @SuppressWarnings("unused")
      private final CallSite callsite;

      private final String description;
      private final Path fileName;
      private final String fixedName;
      private final String name;
      private final List<RefillerParameterDefinition> parameters;

      public ArbitraryRefillerDefinition(
          String name,
          MethodHandle target,
          Path fileName,
          String description,
          List<RefillerParameterDefinition> parameters) {
        this.name = name;
        this.fileName = fileName;
        this.description = description;
        this.parameters = parameters;
        final MethodHandle handle = target.asType(MethodType.methodType(Refiller.class));
        fixedName = name + " refill";
        callsite = installArbitrary(fixedName, handle);
      }

      @Override
      public String description() {
        return description;
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
      public Stream<RefillerParameterDefinition> parameters() {
        return parameters.stream();
      }

      @Override
      public void render(Renderer renderer) {
        renderer
            .methodGen()
            .invokeDynamic(
                fixedName, Type.getMethodDescriptor(A_REFILLER_TYPE), BSM_HANDLE_ARBITRARY);
      }
    }

    private final class ArbitraryStaticSignatureDefintion extends SignatureVariableForStaticSigner {
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final CallSite callsite;

      private final Path fileName;

      private final String fixedName;

      public ArbitraryStaticSignatureDefintion(
          String name, MethodHandle handle, Imyhat type, Path fileName) {
        super(name, type);
        fixedName = name + " static signer";
        callsite = installArbitrary(fixedName, handle);
        this.fileName = fileName;
      }

      @Override
      public Path filename() {
        return fileName;
      }

      @Override
      protected void newInstance(GeneratorAdapter method) {
        method.invokeDynamic(
            fixedName,
            Type.getMethodDescriptor(Type.getType(StaticSigner.class)),
            BSM_HANDLE_ARBITRARY);
      }
    }

    private class FileWrapper implements WatchedFileListener, Definer<T> {
      private final Map<String, ActionDefinition> actions = new ConcurrentHashMap<>();

      private final List<ActionDefinition> actionsFromAnnotations;
      // Hold onto a reference to the callsite so that it isn't garbage collected
      @SuppressWarnings({"unused", "FieldCanBeLocal"})
      private final MutableCallSite callsite;

      private final Map<String, ConstantDefinition> constants = new ConcurrentHashMap<>();
      private final List<ConstantDefinition> constantsFromAnnotations;
      private final Map<String, Deque<InputDataSource>> customSources = new ConcurrentHashMap<>();
      private final Map<String, FunctionDefinition> functions = new ConcurrentHashMap<>();
      private final List<FunctionDefinition> functionsFromAnnotations;
      private final T instance;
      private final Map<String, RefillerDefinition> refillers = new ConcurrentHashMap<>();
      private final List<RefillerDefinition> refillersFromAnnotations;
      private final Map<String, SignatureDefinition> signatures = new ConcurrentHashMap<>();
      private final List<SignatureDefinition> signaturesFromAnnotations;

      public FileWrapper(Path path) {
        instance =
            fileFormat.create(
                path, RuntimeSupport.removeExtension(path, fileFormat.extension()), this);
        // Create a method handle that just returns this instance
        final MethodHandle target = MethodHandles.constant(fileFormat.fileClass(), instance);
        // Update this call site with our current reference. We hold onto the call site
        // because if the olive stops using it, it will be garbage collected
        callsite = CONFIG_FILE_INSTANCES.upsert(path.toString(), target);

        final String instanceName =
            RuntimeSupport.removeExtension(instance.fileName(), fileFormat.extension())
                .replaceAll("[^a-zA-Z0-9_]", "_");

        // Now expose all our plugins to the olive compiler
        constantsFromAnnotations =
            constantTemplates
                .stream()
                .map(t -> t.bind(instanceName, path))
                .collect(Collectors.toList());
        functionsFromAnnotations =
            functionTemplates
                .stream()
                .map(t -> t.bind(instanceName, path))
                .collect(Collectors.toList());
        actionsFromAnnotations =
            actionTemplates
                .stream()
                .map(t -> t.bind(instanceName, path))
                .collect(Collectors.toList());
        signaturesFromAnnotations =
            signatureTemplates
                .stream()
                .map(t -> t.bind(instanceName, path))
                .collect(Collectors.toList());
        refillersFromAnnotations =
            refillTemplates
                .stream()
                .map(t -> t.bind(instanceName, path))
                .collect(Collectors.toList());
      }

      public Stream<ActionDefinition> actions() {
        return Stream.concat(actions.values().stream(), actionsFromAnnotations.stream());
      }

      @Override
      public void clearActions() {
        actions.clear();
      }

      @Override
      public void clearConstants() {
        constants.clear();
      }

      @Override
      public void clearFunctions() {
        functions.clear();
      }

      @Override
      public void clearRefillers() {
        refillers.clear();
      }

      @Override
      public void clearSource() {
        customSources.clear();
      }

      @Override
      public void clearSource(String formatName) {
        customSources.computeIfAbsent(formatName, k -> new ConcurrentLinkedDeque<>()).clear();
      }

      public ConfigurationSection configuration() {
        return new ConfigurationSection(instance.fileName().toString()) {
          @Override
          public void emit(SectionRenderer sectionRenderer) throws XMLStreamException {
            instance.configuration(sectionRenderer);
          }
        };
      }

      public Stream<ConstantDefinition> constants() {
        return Stream.concat(constants.values().stream(), constantsFromAnnotations.stream());
      }

      @Override
      public <A extends Action> void defineAction(
          String name,
          String description,
          Class<A> clazz,
          Supplier<A> supplier,
          Stream<CustomActionParameter<A>> parameters) {
        final MethodHandle handle =
            MH_SUPPLIER_GET.bindTo(supplier).asType(MethodType.methodType(clazz));
        actions.put(
            name,
            new ArbitraryActionDefintition(
                name,
                handle,
                description,
                instance.fileName(),
                Stream.concat(
                    parameters.map(p -> new InvokeDynamicActionParameterDescriptor(name, p)),
                    InvokeDynamicActionParameterDescriptor.findActionDefinitionsByAnnotation(
                        clazz, fileFormat.lookup()))));
      }

      @Override
      public void defineConstant(String name, String description, Imyhat type, Object value) {
        constants.put(
            name,
            new ArbitraryConstantDefinition(
                name,
                MethodHandles.constant(type.javaType(), value),
                type,
                description,
                instance.fileName()));
      }

      @Override
      public <R> void defineConstant(
          String name, String description, ReturnTypeGuarantee<R> type, R value) {
        constants.put(
            name,
            new ArbitraryConstantDefinition(
                name,
                MethodHandles.constant(type.type().javaType(), value),
                type.type(),
                description,
                instance.fileName()));
      }

      @Override
      public <R> void defineConstant(
          String name,
          String description,
          ReturnTypeGuarantee<R> returnType,
          Supplier<R> constant) {

        constants.put(
            name,
            new ArbitraryConstantDefinition(
                name,
                MH_SUPPLIER_GET.bindTo(constant),
                returnType.type(),
                description,
                instance.fileName()));
      }

      @Override
      public <R> void defineDynamicSigner(
          String name,
          ReturnTypeGuarantee<R> returnType,
          Supplier<? extends DynamicSigner<R>> signer) {
        signatures.put(
            name,
            new ArbitraryDynamicSignatureDefintion(
                name, MH_SUPPLIER_GET.bindTo(signer), returnType.type(), instance.fileName()));
      }

      @Override
      public void defineFunction(
          String name,
          String description,
          Imyhat returnType,
          VariadicFunction function,
          FunctionParameter... parameters) {
        final MethodHandle handle =
            MH_VARIADICFUNCTION_APPLY
                .bindTo(function)
                .asCollector(Object[].class, parameters.length)
                .asType(
                    MethodType.methodType(
                        returnType.javaType(),
                        Stream.of(parameters).map(p -> p.type().javaType()).toArray(Class[]::new)));
        functions.put(
            name,
            new ArbitraryFunctionDefinition(
                name, description, instance.fileName(), handle, returnType, parameters));
      }

      @Override
      public <A, R> void defineFunction(
          String name,
          String description,
          ReturnTypeGuarantee<R> returnType,
          String parameterDescription,
          TypeGuarantee<A> parameterType,
          Function<A, R> function) {
        final MethodHandle handle =
            MH_FUNCTION_APPLY
                .bindTo(function)
                .asType(
                    MethodType.methodType(
                        returnType.type().javaType(), parameterType.type().javaType()));
        functions.put(
            name,
            new ArbitraryFunctionDefinition(
                name,
                description,
                instance.fileName(),
                handle,
                returnType.type(),
                new FunctionParameter(parameterDescription, parameterType.type())));
      }

      @Override
      public <A, B, R> void defineFunction(
          String name,
          String description,
          ReturnTypeGuarantee<R> returnType,
          String parameter1Description,
          TypeGuarantee<A> parameter1Type,
          String parameter2Description,
          TypeGuarantee<B> parameter2Type,
          BiFunction<A, B, R> function) {
        final MethodHandle handle =
            MH_BIFUNCTION_APPLY
                .bindTo(function)
                .asType(
                    MethodType.methodType(
                        returnType.type().javaType(),
                        parameter1Type.type().javaType(),
                        parameter2Type.type().javaType()));
        final String fixedName =
            name
                + " "
                + parameter1Type.type().descriptor()
                + " "
                + parameter2Type.type().descriptor()
                + " "
                + returnType.type().descriptor();
        installArbitrary(fixedName, handle);
        functions.put(
            name,
            new FunctionDefinition() {

              @Override
              public String description() {
                return description;
              }

              @Override
              public Path filename() {
                return instance.fileName();
              }

              @Override
              public String name() {
                return name;
              }

              @Override
              public Stream<FunctionParameter> parameters() {
                return Stream.of(
                    new FunctionParameter(parameter1Description, parameter1Type.type()),
                    new FunctionParameter(parameter2Description, parameter2Type.type()));
              }

              @Override
              public void render(GeneratorAdapter methodGen) {
                methodGen.invokeDynamic(
                    fixedName,
                    Type.getMethodDescriptor(
                        returnType.type().apply(TypeUtils.TO_ASM),
                        parameter1Type.type().apply(TypeUtils.TO_ASM),
                        parameter2Type.type().apply(TypeUtils.TO_ASM)),
                    BSM_HANDLE_ARBITRARY);
              }

              @Override
              public void renderStart(GeneratorAdapter methodGen) {
                // Nothing to do.
              }

              @Override
              public Imyhat returnType() {
                return returnType.type();
              }
            });
      }

      @Override
      public void defineRefiller(String name, String description, RefillDefiner refillerDefiner) {
        final RefillInfo<?, ?> info = refillerDefiner.info(Object.class);
        refillers.put(
            name,
            new ArbitraryRefillerDefinition(
                name,
                MH_REFILL_INFO_CREATE.bindTo(info),
                instance.fileName(),
                description,
                Stream.concat(
                        info.parameters()
                            .map(p -> new InvokeDynamicRefillerParameterDescriptor(name, p)),
                        InvokeDynamicRefillerParameterDescriptor
                            .findRefillerDefinitionsByAnnotation(info.type(), fileFormat.lookup()))
                    .collect(Collectors.toList())));
      }

      @Override
      public void defineSource(String formatName, int ttl, JsonInputSource source) {
        AnnotatedInputFormatDefinition.formats()
            .filter(format -> format.name().equals(formatName))
            .forEach(
                format ->
                    customSources
                        .computeIfAbsent(format.name(), k -> new ConcurrentLinkedDeque<>())
                        .add(format.fromJsonStream(instance.fileName().toString(), ttl, source)));
      }

      @Override
      public <R> void defineStaticSigner(
          String name,
          ReturnTypeGuarantee<R> returnType,
          Supplier<? extends StaticSigner<R>> signer) {
        signatures.put(
            name,
            new ArbitraryStaticSignatureDefintion(
                name, MH_SUPPLIER_GET.bindTo(signer), returnType.type(), instance.fileName()));
      }

      public Stream<Object> fetch(String format, boolean readStale) {
        final Queue<DynamicInputDataSource> sources = dynamicSources.get(format);
        final Deque<InputDataSource> customSource = this.customSources.get(format);
        return Stream.concat(
            sources == null
                ? Stream.empty()
                : sources.stream().flatMap(s -> s.fetch(instance, readStale)),
            customSource == null
                ? Stream.empty()
                : customSource.stream().flatMap(s -> s.fetch(readStale)));
      }

      public Stream<Dumper> findDumper(String name, Imyhat... types) {
        return instance.findDumper(name, types);
      }

      public Stream<FunctionDefinition> functions() {
        return Stream.concat(functions.values().stream(), functionsFromAnnotations.stream());
      }

      /**
       * Get the instance this wrapper holds
       *
       * <p>We want plugins to be deletable, but we also need to ensure that the object graph
       * connecting the instance, this wrapper, the olives, and any actions defined by this instance
       * remain connected.
       *
       * <p>The graph looks like this:
       *
       * <ul>
       *   <li>the wrapper holds a reference to the instance
       *   <li>the wrapper holds a reference to a call site holding the instance (hencefore, the
       *       instance callsite)
       *   <li>the plugin manager holds a reference to the wrapper iff the configuration file exists
       *       on disk
       *   <li>the plugin manager holds a weak reference to the wrapper
       *   <li>the olive holds a reference to the instance callsite for any annotation-defined
       *       action, constant, function, or signer
       *   <li>the bootstrap method has a weak reference to the instance callsite
       *   <li>the wrapper holds a reference to all the callsites for user-defined actions,
       *       constants, functions or signers
       *   <li>the olive holds a reference to any of the user-defined callsites
       * </ul>
       *
       * Under this model, the entire object graph can be strongly referenced if the configuration
       * file is present and/or an olive is using annotation-defined things. If the file goes away
       * on disk, and any user-defined things exist that reference the disk, the wrapper may be
       * garbage collected. If the file reappears, any functions, constants, or signers in the olive
       * will be reattached to the new instance. However, any actions that have been handed off to
       * the schedule will not be updated. Therefore, an action must never hold a reference to an
       * instance; it should hold an instance to this class as a supplier of the instance. If there
       * are no actions generated, then the whole graph can be collected.
       */
      @Override
      public T get() {
        return instance;
      }

      public boolean isOverloaded(Set<String> services) {
        return instance.isOverloaded(services);
      }

      public Stream<RefillerDefinition> refillers() {
        return Stream.concat(refillers.values().stream(), refillersFromAnnotations.stream());
      }

      public <F> Stream<Pair<String, F>> searches(ActionFilterBuilder<F> builder) {
        return instance.searches(builder);
      }

      public Stream<SignatureDefinition> signatures() {
        return Stream.concat(signatures.values().stream(), signaturesFromAnnotations.stream());
      }

      public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
        return instance.sourceUrl(localFilePath, line, column, hash);
      }

      @Override
      public void start() {
        instance.start();
      }

      @Override
      public void stop() {
        instance.stop();
      }

      @Override
      public Optional<Integer> update() {
        return instance.update();
      }
    }

    private final List<Binder<ActionDefinition>> actionTemplates = new ArrayList<>();

    private final AutoUpdatingDirectory<FileWrapper> configuration;

    private final List<Binder<ConstantDefinition>> constantTemplates = new ArrayList<>();
    private Map<String, Queue<DynamicInputDataSource>> dynamicSources = new ConcurrentHashMap<>();
    private final F fileFormat;
    private final List<Binder<FunctionDefinition>> functionTemplates = new ArrayList<>();
    private final List<Binder<RefillerDefinition>> refillTemplates = new ArrayList<>();
    private final List<Binder<SignatureDefinition>> signatureTemplates = new ArrayList<>();

    private List<ActionDefinition> staticActions = new ArrayList<>();

    private List<ConstantDefinition> staticConstants = new ArrayList<>();
    private List<FunctionDefinition> staticFunctions = new ArrayList<>();
    private List<RefillerDefinition> staticRefillers = new ArrayList<>();
    private List<SignatureDefinition> staticSignatures = new ArrayList<>();
    private Map<String, Queue<InputDataSource>> staticSources = new ConcurrentHashMap<>();
    private Map<Path, WeakReference<FileWrapper>> wrappers = new ConcurrentHashMap<>();

    public FormatTypeWrapper(F fileFormat) {
      this.fileFormat = fileFormat;
      try {
        for (final Method method : fileFormat.getClass().getMethods()) {
          checkRepositoryMethod(method);
          checkRepositoryAction(method);
          checkRepositorySignature(method);
          checkRepositorySource(method);
        }
        for (final Method method : fileFormat.fileClass().getMethods()) {
          checkInstanceMethod(method);
          checkInstanceAction(method);
          checkInstanceSignature(method);
          checkInstanceSource(method);
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
        System.err.println(
            "Failed to access a method. Did you give the correct instance of Lookup?");
      }
      // Keep plugin configurations in a separate map so if we get back a deleted configuration
      // file, we can reanimate the existing instance rather than creating a new one.
      configuration =
          new AutoUpdatingDirectory<>(
              fileFormat.extension(),
              p -> wrappers.computeIfAbsent(p, x -> new WeakReference<>(new FileWrapper(x))).get());
    }

    public final Stream<ActionDefinition> actions() {
      return Stream.concat(
          staticActions.stream(), configuration.stream().flatMap(FileWrapper::actions));
    }

    private void checkInstanceAction(final Method method) throws IllegalAccessException {
      final ShesmuAction actionAnnotation = method.getAnnotation(ShesmuAction.class);
      if (actionAnnotation != null) {
        if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuAction annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processActionMethod(actionAnnotation, method, true);
      }
    }

    private void checkInstanceMethod(final Method method) throws IllegalAccessException {
      final ShesmuMethod methodAnnotation = method.getAnnotation(ShesmuMethod.class);
      if (methodAnnotation != null) {
        if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuMethod annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processFunctionOrConstantMethod(methodAnnotation, method, true, 0);
      }
    }

    private void checkInstanceSignature(final Method method) throws IllegalAccessException {
      final ShesmuSigner signatureAnnotation = method.getAnnotation(ShesmuSigner.class);
      if (signatureAnnotation != null) {
        if (Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuSigner annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSignatureMethod(signatureAnnotation, method, true);
      }
    }

    private void checkInstanceSource(final Method method) throws IllegalAccessException {
      final ShesmuInputSource sourceAnnotation = method.getAnnotation(ShesmuInputSource.class);
      if (sourceAnnotation != null) {
        if (Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuInputSource annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSourceMethod(method, true);
      }
      final ShesmuJsonInputSource jsonAnnotation =
          method.getAnnotation(ShesmuJsonInputSource.class);
      if (jsonAnnotation != null) {
        if (Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuJsonInputSource annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSourceMethod(method, jsonAnnotation, true);
      }
    }

    private void checkRepositoryAction(final Method method) throws IllegalAccessException {
      final ShesmuAction actionAnnotation = method.getAnnotation(ShesmuAction.class);
      if (actionAnnotation != null) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuAction annotation but is not static.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processActionMethod(actionAnnotation, method, false);
      }
    }

    private void checkRepositoryMethod(final Method method) throws IllegalAccessException {
      final ShesmuMethod methodAnnotation = method.getAnnotation(ShesmuMethod.class);
      if (methodAnnotation != null) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has annotation but is not static.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        final boolean isInstance =
            method.getParameterCount() > 0
                && method.getParameterTypes()[0].isAssignableFrom(fileFormat.fileClass());
        processFunctionOrConstantMethod(methodAnnotation, method, isInstance, isInstance ? 1 : 0);
      }
    }

    private void checkRepositorySignature(final Method method) throws IllegalAccessException {
      final ShesmuSigner signatureAnnotation = method.getAnnotation(ShesmuSigner.class);
      if (signatureAnnotation != null) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuSigner annotation but is not static.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSignatureMethod(signatureAnnotation, method, false);
      }
    }

    private void checkRepositorySource(final Method method) throws IllegalAccessException {
      final ShesmuInputSource sourceAnnotation = method.getAnnotation(ShesmuInputSource.class);
      if (sourceAnnotation != null) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuInputSource annotation but is not static.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSourceMethod(method, false);
      }
      final ShesmuJsonInputSource jsonAnnotation =
          method.getAnnotation(ShesmuJsonInputSource.class);
      if (jsonAnnotation != null) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has ShesmuJsonInputSource annotation but is not static.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSourceMethod(method, jsonAnnotation, false);
      }
    }

    public final Stream<ConstantDefinition> constants() {
      return Stream.concat(
          staticConstants.stream(), configuration.stream().flatMap(FileWrapper::constants));
    }

    public <T> Stream<T> exportSearches(ExportSearch<T> builder) {
      return Stream.concat(
          fileFormat.exportSearches(builder),
          configuration.stream().flatMap(f -> f.instance.exportSearches(builder)));
    }

    public Stream<Object> fetch(String format, boolean readStale) {
      Queue<InputDataSource> sources = staticSources.get(format);
      return Stream.concat(
          sources == null
              ? Stream.empty()
              : sources.stream().flatMap(source -> source.fetch(readStale)),
          configuration.stream().flatMap(f -> f.fetch(format, readStale)));
    }

    public Stream<Dumper> findDumper(String name, Imyhat... types) {
      return Stream.concat(
          fileFormat.findDumper(name, types),
          configuration.stream().flatMap(f -> f.findDumper(name, types)));
    }

    public final Stream<FunctionDefinition> functions() {
      return Stream.concat(
          staticFunctions.stream(), configuration.stream().flatMap(FileWrapper::functions));
    }

    private CallSite installArbitrary(String fixedName, MethodHandle handle) {
      return CONFIG_FILE_ARBITRARY_BINDINGS.upsert(fixedName, handle);
    }

    private DynamicInvoker installMethod(Method method, String prefix, Class<?> returnType)
        throws IllegalAccessException {
      final String mangledMethodName = prefix + method.getName();
      MethodHandle handle = fileFormat.lookup().unreflect(method);
      if (returnType != null) {
        handle = handle.asType(handle.type().changeReturnType(returnType));
      }
      final String methodDescriptor =
          handle.type().dropParameterTypes(0, 1).toMethodDescriptorString();
      CONFIG_FILE_METHOD_BINDINGS.put(
          new Pair<>(fileFormat.fileClass(), mangledMethodName), handle);

      return (methodGen, path) ->
          methodGen.invokeDynamic(mangledMethodName, methodDescriptor, BSM_HANDLE, path);
    }

    public boolean isOverloaded(Set<String> services) {
      return fileFormat.isOverloaded(services)
          || configuration.stream().anyMatch(f -> f.isOverloaded(services));
    }

    public final Stream<ConfigurationSection> listConfiguration() {
      return configuration.stream().map(FileWrapper::configuration);
    }

    private void processActionMethod(ShesmuAction annotation, Method method, boolean isInstance)
        throws IllegalAccessException {
      final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
      if (!Action.class.isAssignableFrom(method.getReturnType())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s is not an action.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      final List<ActionParameterDefinition> parameters =
          InvokeDynamicActionParameterDescriptor.findActionDefinitionsByAnnotation(
                  method.getReturnType().asSubclass(Action.class), fileFormat.lookup())
              .collect(Collectors.toList());
      if (isInstance) {
        final DynamicInvoker invoker = installMethod(method, "action", Action.class);
        actionTemplates.add(
            (instance, path) ->
                new ActionDefinition(
                    name.replace("$", instance),
                    mangleDescription(annotation.description(), instance, path),
                    path,
                    parameters.stream()) {

                  @Override
                  public void initialize(GeneratorAdapter methodGen) {
                    invoker.write(methodGen, path.toString());
                  }
                });
      } else {
        staticActions.add(
            new ArbitraryActionDefintition(
                name,
                fileFormat.lookup().unreflect(method),
                annotation.description(),
                null,
                parameters.stream()));
      }
    }

    private void processFunctionOrConstantMethod(
        ShesmuMethod annotation, Method method, boolean isInstance, int offset)
        throws IllegalAccessException {
      final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
      final Imyhat returnType =
          Imyhat.convert(
              String.format(
                  "Return type of %s in %s",
                  method.getName(), method.getDeclaringClass().getName()),
              annotation.type(),
              method.getGenericReturnType());

      if (method.getParameterCount() == offset) {
        if (isInstance) {
          final DynamicInvoker invoker = installMethod(method, "", null);
          constantTemplates.add(
              (instanceName, path) ->
                  new ConstantDefinition(
                      name.replace("$", instanceName),
                      returnType,
                      mangleDescription(annotation.description(), instanceName, path),
                      path) {

                    @Override
                    protected void load(GeneratorAdapter methodGen) {
                      invoker.write(methodGen, path.toString());
                    }
                  });
        } else {
          final MethodHandle handle = fileFormat.lookup().unreflect(method);
          staticConstants.add(
              new ArbitraryConstantDefinition(
                  name, handle, returnType, annotation.description(), null));
        }
      } else {
        final FunctionParameter[] functionParameters =
            Stream.of(method.getParameters())
                .skip(offset)
                .map(
                    p -> {
                      final ShesmuParameter parameterAnnotation =
                          p.getAnnotation(ShesmuParameter.class);
                      final Imyhat type =
                          Imyhat.convert(
                              String.format(
                                  "Parameter %s from %s in %s",
                                  p.getName(),
                                  method.getName(),
                                  method.getDeclaringClass().getName()),
                              parameterAnnotation == null ? "" : parameterAnnotation.type(),
                              p.getParameterizedType());
                      return new FunctionParameter(
                          parameterAnnotation == null
                              ? p.getName()
                              : parameterAnnotation.description(),
                          type);
                    })
                .toArray(FunctionParameter[]::new);
        if (isInstance) {
          final DynamicInvoker invoker = installMethod(method, "", null);
          functionTemplates.add(
              (instanceName, path) ->
                  new FunctionDefinition() {

                    @Override
                    public String description() {
                      return mangleDescription(annotation.description(), instanceName, path);
                    }

                    @Override
                    public Path filename() {
                      return path;
                    }

                    @Override
                    public String name() {
                      return name.replace("$", instanceName);
                    }

                    @Override
                    public Stream<FunctionParameter> parameters() {
                      return Stream.of(functionParameters);
                    }

                    @Override
                    public void render(GeneratorAdapter methodGen) {
                      invoker.write(methodGen, path.toString());
                    }

                    @Override
                    public void renderStart(GeneratorAdapter methodGen) {
                      // Do nothing
                    }

                    @Override
                    public Imyhat returnType() {
                      return returnType;
                    }
                  });
        } else {
          final MethodHandle handle = fileFormat.lookup().unreflect(method);
          staticFunctions.add(
              new ArbitraryFunctionDefinition(
                  name, annotation.description(), null, handle, returnType, functionParameters));
        }
      }
    }

    private void processRefillMethod(ShesmuRefill annotation, Method method, boolean isInstance)
        throws IllegalAccessException {
      final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
      if (!Refiller.class.isAssignableFrom(method.getReturnType())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s is not a refiller.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      if (method.getGenericParameterTypes().length != 1
          || !(method.getGenericParameterTypes()[0] instanceof TypeVariable)) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s is not a parameterized by one type variable.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      if (Stream.of(((TypeVariable<?>) method.getGenericParameterTypes()[0]).getBounds())
          .anyMatch(x -> !Object.class.equals(x))) {
        throw new IllegalArgumentException(
            String.format(
                "Parameter %s of %s of %s has invalid bounds.",
                method.getGenericParameterTypes()[0],
                method.getName(),
                method.getDeclaringClass().getName()));
      }
      if (!(method.getGenericReturnType() instanceof ParameterizedType)) {
        throw new IllegalArgumentException(
            String.format(
                "Return value of method %s of %s is not a parameterized.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      final ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
      if (parameterizedType.getActualTypeArguments().length != 1
          || !parameterizedType.getActualTypeArguments()[0].equals(
              method.getGenericParameterTypes()[0])) {
        throw new IllegalArgumentException(
            String.format(
                "Return value of method %s of %s is not a parameterized by %s of method.",
                method.getName(),
                method.getDeclaringClass().getName(),
                method.getGenericParameterTypes()[0].getTypeName()));
      }
      final List<RefillerParameterDefinition> parameters =
          InvokeDynamicRefillerParameterDescriptor.findRefillerDefinitionsByAnnotation(
                  method.getReturnType().asSubclass(Refiller.class), fileFormat.lookup())
              .collect(Collectors.toList());
      if (isInstance) {
        final DynamicInvoker invoker = installMethod(method, "refiller", null);
        refillTemplates.add(
            (instance, path) ->
                new RefillerDefinition() {
                  final String description =
                      mangleDescription(annotation.description(), instance, path);
                  final String instanceName = name.replace("$", instance);

                  @Override
                  public String description() {
                    return description;
                  }

                  @Override
                  public Path filename() {
                    return path;
                  }

                  @Override
                  public String name() {
                    return instanceName;
                  }

                  @Override
                  public Stream<RefillerParameterDefinition> parameters() {
                    return parameters.stream();
                  }

                  @Override
                  public void render(Renderer renderer) {
                    invoker.write(renderer.methodGen(), path.toString());
                  }
                });
      } else {
        staticRefillers.add(
            new ArbitraryRefillerDefinition(
                name,
                fileFormat.lookup().unreflect(method),
                null,
                annotation.description(),
                parameters));
      }
    }

    private void processSignatureMethod(ShesmuSigner annotation, Method method, boolean isInstance)
        throws IllegalAccessException {
      final String name = AnnotationUtils.checkName(annotation.name(), method, isInstance);
      if (!DynamicSigner.class.isAssignableFrom(method.getReturnType())
          && !StaticSigner.class.isAssignableFrom(method.getReturnType())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s is not an signer.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      final boolean isDynamic = DynamicSigner.class.isAssignableFrom(method.getReturnType());
      final Imyhat returnType =
          unwrapAndConvert(
              String.format(
                  "Method %s of %s.", method.getName(), method.getDeclaringClass().getName()),
              annotation.type(),
              method.getGenericReturnType());
      if (returnType.isBad()) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s has invalid type descriptor %s.",
                method.getName(), method.getDeclaringClass().getName(), annotation.type()));
      }
      if (isInstance) {
        final DynamicInvoker invoker = installMethod(method, "signer", null);
        if (isDynamic) {
          signatureTemplates.add(
              (instance, path) ->
                  new SignatureVariableForDynamicSigner(name, returnType) {

                    @Override
                    public Path filename() {
                      return path;
                    }

                    @Override
                    protected void newInstance(GeneratorAdapter method) {
                      invoker.write(method, path.toString());
                    }
                  });
        } else {
          signatureTemplates.add(
              (instance, path) ->
                  new SignatureVariableForStaticSigner(name, returnType) {

                    @Override
                    public Path filename() {
                      return path;
                    }

                    @Override
                    protected void newInstance(GeneratorAdapter method) {
                      invoker.write(method, path.toString());
                    }
                  });
        }
      } else {
        if (isDynamic) {
          staticSignatures.add(
              new ArbitraryDynamicSignatureDefintion(
                  name, fileFormat.lookup().unreflect(method), returnType, null));
        } else {
          staticSignatures.add(
              new ArbitraryStaticSignatureDefintion(
                  name, fileFormat.lookup().unreflect(method), returnType, null));
        }
      }
    }

    private void processSourceMethod(Method method, boolean isInstance)
        throws IllegalAccessException {
      if (!Stream.class.equals(method.getReturnType())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s does not return Stream.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      java.lang.reflect.Type returnType = method.getGenericReturnType();
      if (!(returnType instanceof ParameterizedType)) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s does not return Stream<T> or generic type information missing from bytecode.",
                method.getName(), method.getDeclaringClass().getName()));
      }
      java.lang.reflect.Type typeParameter =
          ((ParameterizedType) returnType).getActualTypeArguments()[0];
      if (!(typeParameter instanceof Class)) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s does not return Stream<T> where T is a class. Cannot deal with %s.",
                method.getName(), method.getDeclaringClass().getName(), typeParameter));
      }
      boolean dropBoolean;
      switch (method.getParameterCount()) {
        case 0:
          dropBoolean = true;
          break;
        case 1:
          if (method.getParameterTypes()[0].equals(boolean.class)) {
            dropBoolean = false;
            break;
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "Method %s of %s has argument which is not a boolean.",
                    method.getName(), method.getDeclaringClass().getName()));
          }
        default:
          throw new IllegalArgumentException(
              String.format(
                  "Method %s of %s has too many parameters. Needs to be none or a single boolean.",
                  method.getName(), method.getDeclaringClass().getName()));
      }
      Class<?> dataType = (Class<?>) typeParameter;
      final Consumer<String> writeSource;
      MethodHandle handle = fileFormat.lookup().unreflect(method);
      if (dropBoolean) {
        handle = MethodHandles.dropArguments(handle, handle.type().parameterCount(), boolean.class);
      }
      if (isInstance) {
        final DynamicInputDataSource source =
            MethodHandleProxies.asInterfaceInstance(DynamicInputDataSource.class, handle);
        writeSource =
            name ->
                dynamicSources
                    .computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>())
                    .add(source);
      } else {
        final InputDataSource source =
            MethodHandleProxies.asInterfaceInstance(InputDataSource.class, handle);
        writeSource =
            name ->
                staticSources.computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>()).add(source);
      }
      AnnotatedInputFormatDefinition.formats()
          .filter(inputFormat -> inputFormat.isAssignableFrom(dataType))
          .map(AnnotatedInputFormatDefinition::name)
          .forEach(writeSource);
    }

    private void processSourceMethod(
        Method method, ShesmuJsonInputSource annotation, boolean isInstance)
        throws IllegalAccessException {
      if (!InputStream.class.isAssignableFrom(method.getReturnType())) {
        throw new IllegalArgumentException(
            String.format(
                "Method %s of %s does not return InputStream (or a subclass).",
                method.getName(), method.getDeclaringClass().getName()));
      }
      final Consumer<AnnotatedInputFormatDefinition> writeSource;
      final MethodHandle handle = fileFormat.lookup().unreflect(method);
      final String cacheName =
          method.getDeclaringClass().getCanonicalName() + " " + method.getName();
      if (isInstance) {
        final DynamicInputJsonSource source =
            MethodHandleProxies.asInterfaceInstance(DynamicInputJsonSource.class, handle);
        writeSource =
            format ->
                dynamicSources
                    .computeIfAbsent(format.name(), k -> new ConcurrentLinkedQueue<>())
                    .add(format.fromJsonStream(cacheName, annotation.ttl(), source));
      } else {
        final JsonInputSource source =
            MethodHandleProxies.asInterfaceInstance(JsonInputSource.class, handle);
        writeSource =
            format ->
                staticSources
                    .computeIfAbsent(format.name(), k -> new ConcurrentLinkedQueue<>())
                    .add(format.fromJsonStream(cacheName, annotation.ttl(), source));
      }
      AnnotatedInputFormatDefinition.formats()
          .filter(format -> format.name().equals(annotation.format()))
          .forEach(writeSource);
    }

    public void pushAlerts(String alertJson) {
      fileFormat.pushAlerts(alertJson);
      configuration.stream().forEach(f -> f.instance.pushAlerts(alertJson));
    }

    public Stream<RefillerDefinition> refillers() {
      return Stream.concat(
          staticRefillers.stream(), configuration.stream().flatMap(FileWrapper::refillers));
    }

    public <F> Stream<Pair<String, F>> searches(ActionFilterBuilder<F> builder) {
      return Stream.concat(
          fileFormat.searches(builder), configuration.stream().flatMap(f -> f.searches(builder)));
    }

    public Stream<SignatureDefinition> signatures() {
      return Stream.concat(
          staticSignatures.stream(), configuration.stream().flatMap(FileWrapper::signatures));
    }

    public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
      return Stream.concat(
          fileFormat.sourceUrl(localFilePath, line, column, hash),
          configuration.stream().flatMap(f -> f.sourceUrl(localFilePath, line, column, hash)));
    }

    public void writeJavaScriptRenderer(PrintStream writer) {
      fileFormat.writeJavaScriptRenderer(writer);
    }
  }

  private static final Type A_REFILLER_TYPE = Type.getType(Refiller.class);
  private static final String BSM_DESCRIPTOR_ARBITRARY =
      Type.getMethodDescriptor(
          Type.getType(CallSite.class),
          Type.getType(MethodHandles.Lookup.class),
          Type.getType(String.class),
          Type.getType(MethodType.class));
  private static final Handle BSM_HANDLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(PluginManager.class).getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);
  private static final Handle BSM_HANDLE_ARBITRARY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(PluginManager.class).getInternalName(),
          "bootstrap",
          BSM_DESCRIPTOR_ARBITRARY,
          false);
  private static final CallSiteRegistry<String> CONFIG_FILE_ARBITRARY_BINDINGS =
      new CallSiteRegistry<>();
  private static final CallSiteRegistry<String> CONFIG_FILE_INSTANCES = new CallSiteRegistry<>();
  private static final Map<Pair<Class<?>, String>, MethodHandle> CONFIG_FILE_METHOD_BINDINGS =
      new ConcurrentHashMap<>();

  @SuppressWarnings("rawtypes")
  private static final ServiceLoader<PluginFileType> FILE_FORMATS =
      ServiceLoader.load(PluginFileType.class);

  private static final MethodHandle MH_BIFUNCTION_APPLY;
  private static final MethodHandle MH_FUNCTION_APPLY;
  private static final MethodHandle MH_REFILL_INFO_CREATE;
  private static final MethodHandle MH_SUPPLIER_GET;
  private static final MethodHandle MH_VARIADICFUNCTION_APPLY;
  public static final JarHashRepository<PluginFileType> PLUGIN_HASHES = new JarHashRepository<>();
  private static final MethodHandle SERVICES_REQUIRED;

  static {
    try {
      SERVICES_REQUIRED =
          MethodHandles.publicLookup()
              .findStatic(
                  PluginManager.class,
                  "requiredServices",
                  MethodType.methodType(String[].class, RequiredServices[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  static {
    try {
      MH_SUPPLIER_GET =
          MethodHandles.publicLookup()
              .findVirtual(Supplier.class, "get", MethodType.methodType(Object.class));
      MH_FUNCTION_APPLY =
          MethodHandles.publicLookup()
              .findVirtual(
                  Function.class, "apply", MethodType.methodType(Object.class, Object.class));
      MH_BIFUNCTION_APPLY =
          MethodHandles.publicLookup()
              .findVirtual(
                  BiFunction.class,
                  "apply",
                  MethodType.methodType(Object.class, Object.class, Object.class));
      MH_VARIADICFUNCTION_APPLY =
          MethodHandles.publicLookup()
              .findVirtual(
                  VariadicFunction.class,
                  "apply",
                  MethodType.methodType(Object.class, Object[].class));
      MH_REFILL_INFO_CREATE =
          MethodHandles.publicLookup()
              .findVirtual(
                  Definer.RefillInfo.class, "create", MethodType.methodType(Refiller.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unused")
  public static CallSite bootstrap(Lookup lookup, String methodName, MethodType methodType) {
    return CONFIG_FILE_ARBITRARY_BINDINGS.get(methodName);
  }

  @SuppressWarnings("unused")
  public static CallSite bootstrap(
      Lookup lookup, String methodName, MethodType methodType, String fileName) {
    // We're going to build our call site in two parts
    // First, we get a call site that contains a method handle that just returns the
    // instance associated with our configuration file. That file might be deleted
    // or updated, so we will keep that instance as a weak reference. If the olive
    // using it is garbage collected and the file is deleted, this will drop out of
    // the map. Otherwise, if the file is updated, it can change the instance in the
    // call site and all the olives will use the new one.
    final MutableCallSite instance = CONFIG_FILE_INSTANCES.get(fileName);
    // For the method, we know the type of the thing in the call site, so let's go
    // find a method that can handle it in our cache. We cache these forever because
    // they can't be created or destroyed without reloading the whole server.
    @SuppressWarnings("SuspiciousMethodCalls")
    final MethodHandle methodHandle =
        CONFIG_FILE_METHOD_BINDINGS.get(new Pair<>(instance.type().returnType(), methodName));
    // Now we smash the instance from above with the method. We can create a
    // constant call site (i.e., one that can't be updated) because the method
    // called
    // will never change but the instance is referenced through it's mutable call
    // site, so we can update the instance.
    return new ConstantCallSite(
        MethodHandles.foldArguments(methodHandle, instance.dynamicInvoker()));
  }

  /** Bootstrap method to get a plugin collection as a stream of throttleable services */
  public static CallSite bootstrapServices(
      Lookup lookup, String methodName, MethodType methodType, String... fileNames) {
    // Our goal here is to create list of services that plugins use so we can block an olive if
    // one of those services is throttled. We're given the file names of those plugins as
    // parameters to this method and
    // we're going to want a produce a method that takes no arguments and returns an array of
    // strings, being the service names.

    // Some of the files on that list might be from exported functions, and so have no plugin
    // associated with them
    // First, take the servicesForPlugins(RequiredServices[]) → String[] and convert it to have a
    // fixed
    // number of arguments (RequiredServices[0], RequiredServices[1], ..., RequiredServices[N-1]) →
    // String[] where N
    // is the number of plugins
    MethodHandle collector =
        SERVICES_REQUIRED.asCollector(RequiredServices[].class, fileNames.length);
    // Now, repeatedly fill in the first argument with one of the plugins; we've got a premade
    // callsite for each plugin, so get it from our table and convert it to return the base type
    for (String fileName : fileNames) {
      final CallSite callsite =
          fileName.endsWith(".shesmu")
              ? CompiledGenerator.scriptCallsite(fileName)
              : CONFIG_FILE_INSTANCES.get(fileName);
      collector =
          MethodHandles.foldArguments(
              collector,
              callsite.dynamicInvoker().asType(MethodType.methodType(RequiredServices.class)));
    }
    // Now, we should have a method thats () → String[], which is what we wanted, so shove it in the
    // olive
    return new ConstantCallSite(collector.asType(methodType));
  }

  public static Stream<Pair<String, MethodType>> dumpArbitrary() {
    return CONFIG_FILE_ARBITRARY_BINDINGS.stream();
  }

  public static Stream<Pair<String, MethodType>> dumpBound() {
    return CONFIG_FILE_INSTANCES.stream();
  }

  private static String mangleDescription(String description, String instanceName, Path path) {
    return description.replace("{instance}", instanceName).replace("{file}", path.toString());
  }

  public static String[] requiredServices(RequiredServices... users) {
    return Stream.of(users).flatMap(RequiredServices::services).distinct().toArray(String[]::new);
  }

  private final List<FormatTypeWrapper<?, ?>> formatTypes;

  @SuppressWarnings("Convert2MethodRef")
  public PluginManager() {
    formatTypes = Utils.stream(FILE_FORMATS).map(ff -> create(ff)).collect(Collectors.toList());
  }

  @Override
  public Stream<ActionDefinition> actions() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::actions);
  }

  @Override
  public Stream<ConstantDefinition> constants() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::constants);
  }

  public long count() {
    return (long) formatTypes.size();
  }

  private FormatTypeWrapper<?, ?> create(PluginFileType<?> pluginFileType) {
    PLUGIN_HASHES.add(pluginFileType);
    return new FormatTypeWrapper<>(pluginFileType);
  }

  private void dumpConfig(
      TableRowWriter writer,
      String context,
      Stream<ActionDefinition> actions,
      Stream<ConstantDefinition> constants,
      Stream<FunctionDefinition> functions,
      Stream<String> inputFormats,
      Stream<SignatureDefinition> signatures) {
    actions.forEach(
        action ->
            writer.write(
                Collections.singletonList(
                    new Pair<>("onclick", "window.location = 'actiondefs#" + action.name() + "'")),
                context,
                "Action",
                action.name()));
    constants.forEach(
        constant ->
            writer.write(
                Collections.singletonList(
                    new Pair<>(
                        "onclick", "window.location = 'constantdefs#" + constant.name() + "'")),
                context,
                "Constant",
                constant.name()));
    functions.forEach(
        function ->
            writer.write(
                Collections.singletonList(
                    new Pair<>(
                        "onclick", "window.location = 'functiondefs#" + function.name() + "'")),
                context,
                "Function",
                function.name()));
    inputFormats.forEach(
        format ->
            writer.write(
                Collections.singletonList(
                    new Pair<>("onclick", "window.location = 'inputdefs#" + format)),
                context,
                "Input Source",
                format));

    signatures.forEach(
        signature ->
            writer.write(
                Collections.singletonList(
                    new Pair<>(
                        "onclick", "window.location = 'signaturedefs#" + signature.name() + "'")),
                context,
                "Signature",
                signature.name()));
  }

  public void dumpPluginConfig(TableRowWriter writer) {
    for (final FormatTypeWrapper<?, ?> type : formatTypes) {
      dumpConfig(
          writer,
          type.fileFormat.extension() + " Plugin",
          type.staticActions.stream(),
          type.staticConstants.stream(),
          type.staticFunctions.stream(),
          type.staticSources.keySet().stream(),
          type.staticSignatures.stream());
      type.configuration
          .stream()
          .forEach(
              c ->
                  dumpConfig(
                      writer,
                      c.instance.fileName().toString(),
                      c.actions(),
                      c.constants(),
                      c.functions(),
                      type.dynamicSources.keySet().stream(),
                      c.signatures()));
    }
  }

  public <T> Stream<T> exportSearches(ExportSearch<T> builder) {
    return formatTypes.stream().flatMap(formatType -> formatType.exportSearches(builder));
  }

  @Override
  public Stream<Object> fetch(String format, boolean readStale) {
    return formatTypes.stream().flatMap(f -> f.fetch(format, readStale));
  }

  /**
   * Find a dumper
   *
   * @param name the dumper to find
   * @return the dumper if found, or an empty optional if none is available
   */
  public Optional<Dumper> findDumper(String name, Imyhat... types) {
    return formatTypes.stream().flatMap(f -> f.findDumper(name, types)).findFirst();
  }

  @Override
  public Stream<FunctionDefinition> functions() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::functions);
  }

  /**
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return true if the action should be blocked; false if it may proceed
   */
  public boolean isOverloaded(Set<String> services) {
    return formatTypes.stream().anyMatch(f -> f.isOverloaded(services));
  }

  @Override
  public Stream<ConfigurationSection> listConfiguration() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::listConfiguration);
  }

  public void pushAlerts(String alertJson) {
    for (FormatTypeWrapper<?, ?> type : formatTypes) {
      type.pushAlerts(alertJson);
    }
  }

  @Override
  public Stream<RefillerDefinition> refillers() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::refillers);
  }

  public <F> Stream<Pair<String, F>> searches(ActionFilterBuilder<F> builder) {
    return formatTypes.stream().flatMap(formatType -> formatType.searches(builder));
  }

  @Override
  public Stream<SignatureDefinition> signatures() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::signatures);
  }

  /**
   * Create a URL for a source file
   *
   * @return the URL to the source file or null if not possible
   */
  @Override
  public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
    return formatTypes.stream().flatMap(f -> f.sourceUrl(localFilePath, line, column, hash));
  }

  private Imyhat unwrapAndConvert(String context, String descriptor, java.lang.reflect.Type type) {
    if (!(type instanceof ParameterizedType)) {
      return Imyhat.BAD;
    }
    final ParameterizedType ptype = (ParameterizedType) type;
    return Imyhat.convert(context, descriptor, ptype.getActualTypeArguments()[0]);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    for (FormatTypeWrapper<?, ?> type : formatTypes) {
      type.writeJavaScriptRenderer(writer);
    }
  }
}
