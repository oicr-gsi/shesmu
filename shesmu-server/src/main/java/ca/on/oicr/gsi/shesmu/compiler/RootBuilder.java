package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import io.prometheus.client.Gauge;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Helper to build an {@link ActionGenerator} */
public abstract class RootBuilder {

  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Type A_ACTION_GENERATOR_TYPE = Type.getType(ActionGenerator.class);
  private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
  private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OLIVE_SERVICES_TYPE = Type.getType(OliveServices.class);
  private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);

  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method CTOR_CLASS = new Method("<clinit>", VOID_TYPE, new Type[] {});
  private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});
  private static final Handle HANDLER_IMYHAT =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          A_IMYHAT_TYPE.getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              A_STRING_TYPE,
              Type.getType(MethodType.class)),
          false);
  private static final Method METHOD_ACTION_GENERATOR__CLEAR_GAUGE =
      new Method("clearGauge", VOID_TYPE, new Type[] {});
  private static final Method METHOD_ACTION_GENERATOR__RUN =
      new Method("run", VOID_TYPE, new Type[] {A_OLIVE_SERVICES_TYPE, A_INPUT_PROVIDER_TYPE});
  private static final Method METHOD_ACTION_GENERATOR__RUN_PREPARE =
      new Method("prepare", VOID_TYPE, new Type[] {A_OLIVE_SERVICES_TYPE});

  private static final Method METHOD_ACTION_GENERATOR__TIMEOUT =
      new Method("timeout", INT_TYPE, new Type[] {});
  private static final Method METHOD_BUILD_GAUGE =
      new Method(
          "buildGauge",
          A_GAUGE_TYPE,
          new Type[] {A_STRING_TYPE, A_STRING_TYPE, A_STRING_ARRAY_TYPE});
  private static final Method METHOD_DUMPER__FIND =
      new Method("find", A_DUMPER_TYPE, new Type[] {A_STRING_TYPE, Type.getType(Imyhat[].class)});
  private static final Method METHOD_DUMPER__START = new Method("start", VOID_TYPE, new Type[] {});
  private static final Method METHOD_DUMPER__STOP = new Method("stop", VOID_TYPE, new Type[] {});
  private static final Method METHOD_GAUGE__CLEAR = new Method("clear", VOID_TYPE, new Type[] {});
  private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);

  private static final Method METHOD_OLIVE_SERVICES__FIND_DUMPER =
      new Method(
          "findDumper", A_DUMPER_TYPE, new Type[] {A_STRING_TYPE, Type.getType(Imyhat[].class)});
  private static final Method METHOD_ACTION_GENERATOR__INPUTS =
      new Method("inputs", A_STREAM_TYPE, new Type[] {});

  public static Stream<LoadableValue> proxyCaptured(
      int offset, LoadableValue... capturedVariables) {
    return IntStream.range(0, capturedVariables.length)
        .boxed()
        .map(
            index ->
                new LoadableValue() {

                  @Override
                  public void accept(Renderer renderer) {
                    renderer.methodGen().loadArg(index + offset);
                  }

                  @Override
                  public String name() {
                    return capturedVariables[index].name();
                  }

                  @Override
                  public Type type() {
                    return capturedVariables[index].type();
                  }
                });
  }

  final GeneratorAdapter classInitMethod;
  final ClassVisitor classVisitor;
  final String hash;

  private final Supplier<Stream<ConstantDefinition>> constants;

  private final GeneratorAdapter ctor;

  private final Set<String> dumpers = new HashSet<>();

  private final Set<String> gauges = new HashSet<>();

  private final InputFormatDefinition inputFormatDefinition;

  private final String path;

  private final GeneratorAdapter runMethod;

  private final GeneratorAdapter runPrepare;

  private final Label runStartLabel;
  private final Type selfType;

  private final Supplier<Stream<SignatureDefinition>> signatures;
  private final Set<String> usedFormats = new HashSet<>();
  private final List<LoadableValue> userDefinedConstants = new ArrayList<>();

  public RootBuilder(
      String hash,
      String name,
      String path,
      InputFormatDefinition inputFormatDefinition,
      int timeout,
      Supplier<Stream<ConstantDefinition>> constants,
      Supplier<Stream<SignatureDefinition>> signatures) {
    this.signatures = signatures;
    this.hash = hash;
    this.path = path;
    this.inputFormatDefinition = inputFormatDefinition;
    this.constants = constants;
    selfType = Type.getObjectType(name);
    usedFormats.add(inputFormatDefinition.name());

    classVisitor = createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        name,
        null,
        A_ACTION_GENERATOR_TYPE.getInternalName(),
        null);
    classVisitor.visitSource(path, null);
    ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_DEFAULT, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_ACTION_GENERATOR_TYPE, CTOR_DEFAULT);

    runPrepare =
        new GeneratorAdapter(
            Opcodes.ACC_PRIVATE, METHOD_ACTION_GENERATOR__RUN_PREPARE, null, null, classVisitor);
    runPrepare.visitCode();

    runMethod =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNCHRONIZED,
            METHOD_ACTION_GENERATOR__RUN,
            null,
            null,
            classVisitor);
    runMethod.visitCode();
    runMethod.loadThis();
    runMethod.loadArg(0);
    runMethod.invokeVirtual(selfType, METHOD_ACTION_GENERATOR__RUN_PREPARE);
    runStartLabel = runMethod.mark();

    classInitMethod =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, CTOR_CLASS, null, null, classVisitor);
    classInitMethod.visitCode();

    final GeneratorAdapter timeoutMethod =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC, METHOD_ACTION_GENERATOR__TIMEOUT, null, null, classVisitor);
    timeoutMethod.visitCode();
    timeoutMethod.push(timeout);
    timeoutMethod.returnValue();
    timeoutMethod.visitMaxs(0, 0);
    timeoutMethod.visitEnd();
  }

  /**
   * Add a check that will stop processing olives
   *
   * @param predicate generate code that leaves a boolean value on the stack; if the value is true,
   *     the script will be aborted
   */
  public void addGuard(Consumer<GeneratorAdapter> predicate) {
    predicate.accept(runMethod);
    final Label skip = runMethod.newLabel();
    runMethod.ifZCmp(GeneratorAdapter.EQ, skip);
    runMethod.visitInsn(Opcodes.RETURN);
    runMethod.mark(skip);
  }

  /** Create a new “Define” olive */
  public OliveDefineBuilder buildDefineOlive(String name, Stream<? extends Target> parameters) {
    return new OliveDefineBuilder(this, name, parameters);
  }

  /**
   * Create a new “Run” olive
   *
   * @param line the line in the source file this olive starts on
   * @param signableNames
   */
  public final OliveBuilder buildRunOlive(int line, int column, Set<String> signableNames) {
    return new OliveBuilder(
        this,
        inputFormatDefinition,
        line,
        column,
        inputFormatDefinition
            .baseStreamVariables()
            .filter(
                t -> t.flavour() == Flavour.STREAM_SIGNABLE && signableNames.contains(t.name())));
  }

  public Stream<LoadableValue> constants(boolean allowUserDefined) {
    final Stream<LoadableValue> externalConstants =
        constants.get().map(ConstantDefinition::asLoadable);
    return allowUserDefined
        ? Stream.concat(userDefinedConstants.stream(), externalConstants)
        : externalConstants;
  }

  /** Create a new class for this program. */
  protected abstract ClassVisitor createClassVisitor();

  public void defineConstant(String name, Type type, Consumer<GeneratorAdapter> loader) {
    final String fieldName = name + "$constant";
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, fieldName, type.getDescriptor(), null, null)
        .visitEnd();

    runMethod.loadThis();
    loader.accept(runMethod);
    runMethod.putField(selfType, fieldName, type);
    userDefinedConstants.add(
        new LoadableValue() {

          @Override
          public void accept(Renderer renderer) {
            renderer.methodGen().loadThis();
            renderer.methodGen().getField(selfType, fieldName, type);
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public Type type() {
            return type;
          }
        });
  }

  /** Complete bytecode generation. */
  public final void finish() {
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    classInitMethod.visitInsn(Opcodes.RETURN);
    classInitMethod.visitMaxs(0, 0);
    classInitMethod.visitEnd();

    dumpers.forEach(
        dumper -> {
          runMethod.loadThis();
          runMethod.getField(selfType, dumper, A_DUMPER_TYPE);
          runMethod.invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__STOP);
        });
    final Label endOfRun = runMethod.mark();

    runMethod.visitInsn(Opcodes.RETURN);

    if (!dumpers.isEmpty()) {
      // Generate a finally block to clean up all our dumpers
      runMethod.catchException(runStartLabel, endOfRun, null);
      dumpers.forEach(
          dumper -> {
            runMethod.loadThis();
            runMethod.getField(selfType, dumper, A_DUMPER_TYPE);
            runMethod.invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__STOP);
          });
      runMethod.throwException();
    }
    runMethod.visitMaxs(0, 0);
    runMethod.visitEnd();

    gauges.forEach(
        gauge -> {
          runPrepare.loadThis();
          runPrepare.getField(selfType, gauge, A_GAUGE_TYPE);
          runPrepare.invokeVirtual(A_GAUGE_TYPE, METHOD_GAUGE__CLEAR);
        });
    runPrepare.visitInsn(Opcodes.RETURN);
    runPrepare.visitMaxs(0, 0);
    runPrepare.visitEnd();

    GeneratorAdapter inputFormatsMethod =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC, METHOD_ACTION_GENERATOR__INPUTS, null, null, classVisitor);
    inputFormatsMethod.visitCode();
    inputFormatsMethod.push(usedFormats.size());
    inputFormatsMethod.newArray(A_OBJECT_TYPE);
    int index = 0;
    for (String format : usedFormats) {
      inputFormatsMethod.dup();
      inputFormatsMethod.push(index++);
      inputFormatsMethod.push(format);
      inputFormatsMethod.arrayStore(A_OBJECT_TYPE);
    }
    inputFormatsMethod.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        A_STREAM_TYPE.getInternalName(),
        "of",
        Type.getMethodDescriptor(A_STREAM_TYPE, Type.getType(Object[].class)),
        true);
    inputFormatsMethod.returnValue();
    inputFormatsMethod.visitMaxs(0, 0);
    inputFormatsMethod.visitEnd();

    classVisitor.visitEnd();
  }

  public InputFormatDefinition inputFormatDefinition() {
    return inputFormatDefinition;
  }

  public void loadDumper(String dumper, GeneratorAdapter methodGen, Imyhat... types) {
    final String fieldName = "Dumper " + dumper;
    if (!dumpers.contains(fieldName)) {
      classVisitor
          .visitField(Opcodes.ACC_PRIVATE, fieldName, A_DUMPER_TYPE.getDescriptor(), null, null)
          .visitEnd();
      runPrepare.loadThis();
      runPrepare.loadArg(0);
      runPrepare.push(dumper);
      runPrepare.push(types.length);
      runPrepare.newArray(A_IMYHAT_TYPE);
      for (int i = 0; i < types.length; i++) {
        runPrepare.dup();
        runPrepare.push(i);
        runPrepare.invokeDynamic(types[i].descriptor(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
        runPrepare.arrayStore(A_IMYHAT_TYPE);
      }
      runPrepare.invokeInterface(A_OLIVE_SERVICES_TYPE, METHOD_OLIVE_SERVICES__FIND_DUMPER);
      runPrepare.putField(selfType, fieldName, A_DUMPER_TYPE);
      dumpers.add(fieldName);
    }
    methodGen.loadThis();
    methodGen.getField(selfType, fieldName, A_DUMPER_TYPE);
  }

  public final void useInputFormat(InputFormatDefinition format) {
    usedFormats.add(format.name());
  }

  public final void loadGauge(
      String metricName, String help, List<String> labelNames, GeneratorAdapter methodGen) {
    final String fieldName = "g$" + metricName;
    if (!gauges.contains(metricName)) {
      classVisitor
          .visitField(Opcodes.ACC_PRIVATE, fieldName, A_GAUGE_TYPE.getDescriptor(), null, null)
          .visitEnd();
      ctor.loadThis();
      ctor.loadThis();
      ctor.push(metricName);
      ctor.push(help);
      ctor.push(labelNames.size());
      ctor.newArray(A_STRING_TYPE);
      for (int i = 0; i < labelNames.size(); i++) {
        ctor.dup();
        ctor.push(i);
        ctor.push(labelNames.get(i));
        ctor.arrayStore(A_STRING_TYPE);
      }
      ctor.invokeVirtual(selfType, METHOD_BUILD_GAUGE);
      ctor.putField(selfType, fieldName, A_GAUGE_TYPE);
      gauges.add(fieldName);
    }
    methodGen.loadThis();
    methodGen.getField(selfType, fieldName, A_GAUGE_TYPE);
  }

  /**
   * Get the renderer for {@link ActionGenerator#run(OliveServices, InputProvider)}
   *
   * <p>No stream variables are available in this context
   */
  public final Renderer rootRenderer(boolean allowUserDefined) {
    return new Renderer(
        this,
        runMethod,
        -1,
        null,
        constants(allowUserDefined),
        (renderer, name) -> {
          throw new IllegalArgumentException(
              String.format("Signature variable %s not defined in root context.", name));
        });
  }

  /** Get the type of the class being generated */
  public final Type selfType() {
    return selfType;
  }

  public Stream<SignatureDefinition> signatureVariables() {
    return signatures.get();
  }

  public String sourcePath() {
    return path;
  }
}
