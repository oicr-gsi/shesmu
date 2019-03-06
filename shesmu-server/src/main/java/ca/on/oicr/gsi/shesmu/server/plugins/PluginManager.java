package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureVariableForDynamicSigner;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureVariableForStaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.ShesmuSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.StaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.SourceLocation.SourceLoctionLinker;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.status.TableRowWriter;
import java.io.PrintStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    implements DefinitionRepository, InputProvider, SourceLoctionLinker {
  private interface Binder<D> {
    D bind(String name, Path path);
  }

  private interface DynamicInvoker {
    void write(GeneratorAdapter methodGen, String pathToInstance);
  }

  private class FormatTypeWrapper<F extends PluginFileType<T>, T extends PluginFile> {
    private final class ArbitraryActionDefintition extends ActionDefinition {
      @SuppressWarnings("unused")
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
      @SuppressWarnings("unused")
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
      @SuppressWarnings("unused")
      private final CallSite callsite;

      private final String fixedName;

      public ArbitraryDynamicSignatureDefintion(String name, MethodHandle handle, Imyhat type) {
        super(name, type);
        fixedName = name + " static signer";
        callsite = installArbitrary(fixedName, handle);
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

      @SuppressWarnings("unused")
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

    private final class ArbitraryStaticSignatureDefintion extends SignatureVariableForStaticSigner {
      @SuppressWarnings("unused")
      private final CallSite callsite;

      private final String fixedName;

      public ArbitraryStaticSignatureDefintion(String name, MethodHandle handle, Imyhat type) {
        super(name, type);
        fixedName = name + " static signer";
        callsite = installArbitrary(fixedName, handle);
      }

      @Override
      protected void newInstance(GeneratorAdapter method) {
        method.invokeDynamic(
            fixedName,
            Type.getMethodDescriptor(Type.getType(StaticSigner.class)),
            BSM_HANDLE_ARBITRARY);
      }
    }

    private class FileWrapper implements WatchedFileListener, Definer {
      private final Map<String, ActionDefinition> actions = new ConcurrentHashMap<>();

      private final List<ActionDefinition> actionsFromAnnotations;
      // Hold onto a reference to the callsite so that it isn't garbage collected
      @SuppressWarnings("unused")
      private final MutableCallSite callsite;

      private final Map<String, ConstantDefinition> constants = new ConcurrentHashMap<>();
      private final List<ConstantDefinition> constantsFromAnnotations;
      private final Map<String, FunctionDefinition> functions = new ConcurrentHashMap<>();
      private final List<FunctionDefinition> functionsFromAnnotations;
      private final T instance;
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
            RuntimeSupport.removeExtension(instance.fileName(), fileFormat.extension());

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
          Stream<CustomActionParameter<A, ?>> parameters) {
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
          String name, String description, TypeGuarantee<R> type, R value) {
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
          String name, String description, TypeGuarantee<R> returnType, Supplier<R> constant) {

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
          String name, TypeGuarantee<R> returnType, Supplier<? extends DynamicSigner<R>> signer) {
        signatures.put(
            name,
            new ArbitraryDynamicSignatureDefintion(
                name, MH_SUPPLIER_GET.bindTo(signer), returnType.type()));
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
          TypeGuarantee<R> returnType,
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
          TypeGuarantee<R> returnType,
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
      public <R> void defineStaticSigner(
          String name, TypeGuarantee<R> returnType, Supplier<? extends StaticSigner<R>> signer) {
        signatures.put(
            name,
            new ArbitraryStaticSignatureDefintion(
                name, MH_SUPPLIER_GET.bindTo(signer), returnType.type()));
      }

      public Stream<Object> fetch(String format) {
        final Queue<DynamicInputDataSource> sources = dynamicSources.get(format);
        return sources == null ? Stream.empty() : sources.stream().flatMap(s -> s.fetch(instance));
      }

      public Stream<Dumper> findDumper(String name, Imyhat... types) {
        return instance.findDumper(name, types);
      }

      public Stream<FunctionDefinition> functions() {
        return Stream.concat(functions.values().stream(), functionsFromAnnotations.stream());
      }

      public boolean isOverloaded(Set<String> services) {
        return instance.isOverloaded(services);
      }

      public Stream<SignatureDefinition> signatures() {
        return Stream.concat(signatures.values().stream(), signaturesFromAnnotations.stream());
      }

      public Stream<String> sourceUrl(
          String localFilePath, int line, int column, Instant compileTime) {
        return instance.sourceUrl(localFilePath, line, column, compileTime);
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

    private final List<Binder<SignatureDefinition>> signatureTemplates = new ArrayList<>();

    private List<ActionDefinition> staticActions = new ArrayList<>();

    private List<ConstantDefinition> staticConstants = new ArrayList<>();
    private List<FunctionDefinition> staticFunctions = new ArrayList<>();
    private List<SignatureDefinition> staticSignatures = new ArrayList<>();
    private Map<String, Queue<InputDataSource>> staticSources = new ConcurrentHashMap<>();

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
      configuration = new AutoUpdatingDirectory<>(fileFormat.extension(), FileWrapper::new);
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
                  "Method %s of %s has ShesmuSigner annotation but is not virtual.",
                  method.getName(), method.getDeclaringClass().getName()));
        }
        processSourceMethod(method, true);
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
    }

    public final Stream<ConstantDefinition> constants() {
      return Stream.concat(
          staticConstants.stream(), configuration.stream().flatMap(FileWrapper::constants));
    }

    public Stream<Object> fetch(String format) {
      Queue<InputDataSource> sources = staticSources.get(format);
      return Stream.concat(
          sources == null ? Stream.empty() : sources.stream().flatMap(InputDataSource::fetch),
          configuration.stream().flatMap(f -> f.fetch(format)));
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
      final String name = AnnotationUtils.checkName(annotation.name(), method, true);
      final Imyhat returnType =
          Imyhat.convert(
              String.format(
                  "Return type of %s in %s",
                  method.getName(), method.getDeclaringClass().getName()),
              annotation.type(),
              method.getReturnType());

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
                              p.getType());
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
      final Imyhat returnType = Imyhat.parse(annotation.type());
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
                    protected void newInstance(GeneratorAdapter method) {
                      invoker.write(method, path.toString());
                    }
                  });
        } else {
          signatureTemplates.add(
              (instance, path) ->
                  new SignatureVariableForStaticSigner(name, returnType) {

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
                  name, fileFormat.lookup().unreflect(method), returnType));
        } else {
          staticSignatures.add(
              new ArbitraryStaticSignatureDefintion(
                  name, fileFormat.lookup().unreflect(method), returnType));
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
      Class<?> dataType = (Class<?>) typeParameter;
      final Consumer<String> writeSource;
      if (isInstance) {
        DynamicInputDataSource source =
            MethodHandleProxies.asInterfaceInstance(
                DynamicInputDataSource.class, fileFormat.lookup().unreflect(method));
        writeSource =
            name ->
                dynamicSources
                    .computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>())
                    .add(source);
      } else {
        InputDataSource source =
            MethodHandleProxies.asInterfaceInstance(
                InputDataSource.class, fileFormat.lookup().unreflect(method));
        writeSource =
            name ->
                staticSources.computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>()).add(source);
      }
      AnnotatedInputFormatDefinition.formats()
          .filter(inputFormat -> inputFormat.isAssignableFrom(dataType))
          .map(AnnotatedInputFormatDefinition::name)
          .forEach(writeSource);
    }

    public void pushAlerts(String alertJson) {
      fileFormat.pushAlerts(alertJson);
      configuration.stream().forEach(f -> f.instance.pushAlerts(alertJson));
    }

    public Stream<SignatureDefinition> signatures() {
      return Stream.concat(
          staticSignatures.stream(), configuration.stream().flatMap(FileWrapper::signatures));
    }

    public Stream<String> sourceUrl(String localFilePath, int line, int column, Instant time) {
      return Stream.concat(
          fileFormat.sourceUrl(localFilePath, line, column, time),
          configuration.stream().flatMap(f -> f.sourceUrl(localFilePath, line, column, time)));
    }

    public void writeJavaScriptRenderer(PrintStream writer) {
      fileFormat.writeJavaScriptRenderer(writer);
    }
  }

  public static CallSite bootstrap(Lookup lookup, String methodName, MethodType methodType) {
    return CONFIG_FILE_ARBITRARY_BINDINGS.get(methodName);
  }

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

  public static Stream<Pair<String, MethodType>> dumpArbitrary() {
    return CONFIG_FILE_ARBITRARY_BINDINGS.stream();
  }

  public static Stream<Pair<String, MethodType>> dumpBound() {
    return CONFIG_FILE_INSTANCES.stream();
  }

  private static String mangleDescription(String description, String instanceName, Path path) {
    return description.replace("{instance}", instanceName).replace("{file}", path.toString());
  }

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
  private static final MethodHandle MH_SUPPLIER_GET;
  private static final MethodHandle MH_VARIADICFUNCTION_APPLY;

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
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private final List<FormatTypeWrapper<?, ?>> formatTypes;

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

  @Override
  public Stream<Object> fetch(String format) {
    return formatTypes.stream().flatMap(f -> f.fetch(format));
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
  public Stream<SignatureDefinition> signatures() {
    return formatTypes.stream().flatMap(FormatTypeWrapper::signatures);
  }

  /**
   * Create a URL for a source file
   *
   * @return the URL to the source file or null if not possible
   */
  @Override
  public Stream<String> sourceUrl(String localFilePath, int line, int column, Instant time) {
    return formatTypes.stream().flatMap(f -> f.sourceUrl(localFilePath, line, column, time));
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    for (FormatTypeWrapper<?, ?> type : formatTypes) {
      type.writeJavaScriptRenderer(writer);
    }
  }
}
