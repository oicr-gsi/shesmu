package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import io.prometheus.client.Gauge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Helper to build bytecode for “olives” (decision-action stanzas) */
public abstract class BaseOliveBuilder {
  private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
  private static final Type A_BIFUNCTION_TYPE = Type.getType(BiFunction.class);
  private static final Type A_BIPREDICATE_TYPE = Type.getType(BiPredicate.class);
  private static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
  protected static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
  protected static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
  protected static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  private static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
  protected static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  protected static final Type A_OLIVE_SERVICES_TYPE = Type.getType(OliveServices.class);
  private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);
  private static final Method METHOD_COMPARATOR__COMPARING =
      new Method("comparing", A_COMPARATOR_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_COMPARATOR__REVERSED =
      new Method("reversed", A_COMPARATOR_TYPE, new Type[] {});
  private static final Method METHOD_EQUALS =
      new Method("equals", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  protected static final Method METHOD_FUNCTION__APPLY =
      new Method("apply", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_HASH_CODE = new Method("hashCode", INT_TYPE, new Type[] {});
  protected static final Method METHOD_INPUT_PROVIDER__FETCH =
      new Method("fetch", A_STREAM_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method METHOD_LEFT_JOIN =
      new Method(
          "leftJoin",
          A_STREAM_TYPE,
          new Type[] {
            A_STREAM_TYPE,
            A_STREAM_TYPE,
            A_FUNCTION_TYPE,
            A_FUNCTION_TYPE,
            A_BIFUNCTION_TYPE,
            A_FUNCTION_TYPE,
            A_BICONSUMER_TYPE
          });
  private static final Method METHOD_MONITOR =
      new Method(
          "monitor", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE, A_GAUGE_TYPE, A_FUNCTION_TYPE});
  private static final Method METHOD_OLIVE_SERVICES__MEASURE_FLOW =
      new Method(
          "measureFlow",
          A_STREAM_TYPE,
          new Type[] {A_STREAM_TYPE, A_STRING_TYPE, INT_TYPE, INT_TYPE, INT_TYPE, INT_TYPE});
  private static final Method METHOD_PICK =
      new Method(
          "pick",
          A_STREAM_TYPE,
          new Type[] {
            A_STREAM_TYPE, A_TO_INT_FUNCTION_TYPE, A_BIPREDICATE_TYPE, A_COMPARATOR_TYPE
          });
  private static final Method METHOD_REGROUP =
      new Method(
          "regroup", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE, A_FUNCTION_TYPE, A_BICONSUMER_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__JOIN =
      new Method(
          "join",
          A_STREAM_TYPE,
          new Type[] {
            A_STREAM_TYPE, A_STREAM_TYPE, A_FUNCTION_TYPE, A_FUNCTION_TYPE, A_BIFUNCTION_TYPE
          });
  private static final Method METHOD_STREAM__FILTER =
      new Method("filter", A_STREAM_TYPE, new Type[] {A_PREDICATE_TYPE});
  private static final Method METHOD_STREAM__MAP =
      new Method("map", A_STREAM_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_STREAM__PEEK =
      new Method("peek", A_STREAM_TYPE, new Type[] {A_CONSUMER_TYPE});

  private Type currentType;

  protected final InputFormatDefinition initialFormat;

  protected final RootBuilder owner;
  protected final List<Consumer<Renderer>> steps = new ArrayList<>();

  public BaseOliveBuilder(RootBuilder owner, InputFormatDefinition initialFormat) {
    this.owner = owner;
    this.initialFormat = initialFormat;
    currentType = initialFormat.type();
  }

  /**
   * Create a call clause in an olive
   *
   * @param defineOlive the define olive to run
   * @param arguments the arguments to pass as parameters to the olive
   */
  public final void call(OliveDefineBuilder defineOlive, Stream<Consumer<Renderer>> arguments) {
    final List<Consumer<Renderer>> arglist = arguments.collect(Collectors.toList());
    if (arglist.size() != defineOlive.parameters()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid number of arguments for call. Got %d, expected %d.",
              arglist.size(), defineOlive.parameters()));
    }
    if (!currentType.equals(owner.inputFormatDefinition().type())) {
      throw new IllegalArgumentException("Cannot call on a transformed data stream.");
    }
    steps.add(
        renderer -> {
          renderer.methodGen().loadThis();
          renderer.methodGen().swap();
          loadOliveServices(renderer.methodGen());
          loadInputProvider(renderer.methodGen());
          loadOwnerSourceLocation(renderer.methodGen());
          owner.signatureVariables().forEach(signer -> loadSigner(signer, renderer));
          for (int i = 0; i < arglist.size(); i++) {
            arglist.get(i).accept(renderer);
          }
          renderer.methodGen().invokeVirtual(owner.selfType(), defineOlive.method());
        });
    currentType = defineOlive.currentType();
  }

  /**
   * Gets the current type of an olive
   *
   * <p>Due to grouping clauses, the type flowing through an olive may change. This is the type at
   * the current point in the sequence.
   */
  protected Type currentType() {
    return currentType;
  }

  protected abstract void emitSigner(SignatureDefinition name, Renderer renderer);

  /**
   * Create a “Where” clause in a olive.
   *
   * @param capturedVariables A collection of variables that must be available in the filter clause.
   *     These will be available in the resulting method
   * @return a method generator for the body of the clause
   */
  @SafeVarargs
  public final Renderer filter(int line, int column, LoadableValue... capturedVariables) {
    final Type type = currentType;
    final LambdaBuilder lambda =
        new LambdaBuilder(
            owner,
            String.format("Where %d:%d", line, column),
            LambdaBuilder.predicate(type),
            capturedVariables);
    steps.add(
        renderer -> {
          lambda.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });
    return lambda.renderer(type, this::emitSigner);
  }

  public final JoinBuilder join(
      int line,
      int column,
      InputFormatDefinition innerType,
      Imyhat keyType,
      LoadableValue... capturedVariables) {
    final String className = String.format("shesmu/dyn/Join %d:%d", line, column);

    final Type oldType = currentType;
    final Type newType = Type.getObjectType(className);
    currentType = newType;

    owner.useInputFormat(innerType);

    final LambdaBuilder outerKeyLambda =
        new LambdaBuilder(
            owner,
            String.format("Join %d:%d Outer 🔑", line, column),
            LambdaBuilder.function(keyType, oldType),
            capturedVariables);
    final LambdaBuilder innerKeyLambda =
        new LambdaBuilder(
            owner,
            String.format("Join %d:%d Inner 🔑", line, column),
            LambdaBuilder.function(keyType, innerType.type()),
            capturedVariables);

    steps.add(
        renderer -> {
          loadInputProvider(renderer.methodGen());
          renderer.methodGen().push(innerType.name());
          renderer.methodGen().invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);

          outerKeyLambda.push(renderer);
          innerKeyLambda.push(renderer);
          LambdaBuilder.pushNew(
              renderer, LambdaBuilder.bifunction(newType, oldType, innerType.type()));

          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JOIN);
        });

    final Renderer outerKeyMethodGen = outerKeyLambda.renderer(oldType, this::emitSigner);
    final Renderer innerKeyMethodGen = innerKeyLambda.renderer(oldType, this::emitSigner);
    return new JoinBuilder(
        owner, newType, oldType, innerType.type(), outerKeyMethodGen, innerKeyMethodGen);
  }

  public final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin(
      int line,
      int column,
      InputFormatDefinition innerType,
      Imyhat keyType,
      LoadableValue... capturedVariables) {
    final String joinedClassName =
        String.format("shesmu/dyn/LeftJoinTemporary %d:%d", line, column);
    final String outputClassName = String.format("shesmu/dyn/LeftJoin %d:%d", line, column);

    final Type oldType = currentType;
    final Type joinedType = Type.getObjectType(joinedClassName);
    final Type newType = Type.getObjectType(outputClassName);
    currentType = newType;

    owner.useInputFormat(innerType);

    final LambdaBuilder newMethod =
        new LambdaBuilder(
            owner,
            String.format("LeftJoin %d:%d✨", line, column),
            LambdaBuilder.function(newType, joinedType),
            capturedVariables);
    final LambdaBuilder outerKeyLambda =
        new LambdaBuilder(
            owner,
            String.format("LeftJoin %d:%d Outer 🔑", line, column),
            LambdaBuilder.function(keyType, oldType),
            capturedVariables);
    final LambdaBuilder innerKeyLambda =
        new LambdaBuilder(
            owner,
            String.format("LeftJoin %d:%d Inner 🔑", line, column),
            LambdaBuilder.function(keyType, innerType.type()),
            capturedVariables);
    final LambdaBuilder collectLambda =
        new LambdaBuilder(
            owner,
            String.format("LeftJoin %d:%d 🧲", line, column),
            LambdaBuilder.biconsumer(newType, joinedType),
            capturedVariables);

    steps.add(
        renderer -> {
          loadInputProvider(renderer.methodGen());
          renderer.methodGen().push(innerType.name());
          renderer.methodGen().invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);

          outerKeyLambda.push(renderer);
          innerKeyLambda.push(renderer);
          LambdaBuilder.pushNew(
              renderer, LambdaBuilder.bifunction(joinedType, oldType, innerType.type()));
          newMethod.push(renderer);
          collectLambda.push(renderer);

          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_LEFT_JOIN);

          LambdaBuilder.pushVirtual(
              renderer,
              RegroupVariablesBuilder.METHOD_IS_OK.getName(),
              LambdaBuilder.predicate(newType));
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });

    final Renderer newMethodGen = newMethod.renderer(joinedType, this::emitSigner);
    final Renderer outerKeyMethodGen = outerKeyLambda.renderer(oldType, this::emitSigner);
    final Renderer innerKeyMethodGen = innerKeyLambda.renderer(innerType.type(), this::emitSigner);
    final Renderer collectedMethodGen = collectLambda.renderer(joinedType, 1, this::emitSigner);

    return new Pair<>(
        new JoinBuilder(
            owner, joinedType, oldType, innerType.type(), outerKeyMethodGen, innerKeyMethodGen),
        new RegroupVariablesBuilder(
            owner, outputClassName, newMethodGen, collectedMethodGen, capturedVariables.length));
  }

  public final LetBuilder let(int line, int column, LoadableValue... capturedVariables) {
    final String className = String.format("shesmu/dyn/Let %d:%d", line, column);

    final Type oldType = currentType;
    final Type newType = Type.getObjectType(className);
    currentType = newType;

    final LambdaBuilder createLambda =
        new LambdaBuilder(
            owner,
            String.format("Let %d:%d", line, column),
            LambdaBuilder.function(newType, oldType),
            capturedVariables);

    steps.add(
        renderer -> {
          createLambda.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
        });

    final Renderer createMethodGen = createLambda.renderer(oldType, this::emitSigner);

    return new LetBuilder(owner, newType, createMethodGen);
  }

  public void line(int line) {
    steps.add(renderer -> renderer.mark(line));
  }

  protected abstract void loadInputProvider(GeneratorAdapter method);

  protected abstract void loadOliveServices(GeneratorAdapter method);

  protected abstract void loadOwnerSourceLocation(GeneratorAdapter method);

  protected abstract void loadSigner(SignatureDefinition variable, Renderer renderer);

  /** Stream of all the parameters available for capture/use in the clauses. */
  public abstract Stream<LoadableValue> loadableValues();

  /** Measure how much data goes through this olive clause. */
  public final void measureFlow(String filename, int line, int column) {
    steps.add(
        renderer -> {
          loadOliveServices(renderer.methodGen());
          renderer.methodGen().swap();
          renderer.methodGen().push(filename);
          renderer.methodGen().push(line);
          renderer.methodGen().push(column);
          loadOwnerSourceLocation(renderer.methodGen());
          renderer
              .methodGen()
              .invokeInterface(A_OLIVE_SERVICES_TYPE, METHOD_OLIVE_SERVICES__MEASURE_FLOW);
        });
  }

  /**
   * Create a “Monitor” clause in an olive
   *
   * @param metricName the Prometheus metric name
   * @param help the help text to export
   * @param names the names of the labels
   * @param capturedVariables the variables needed in the method that computes the label values
   */
  public Renderer monitor(
      int line,
      int column,
      String metricName,
      String help,
      List<String> names,
      LoadableValue[] capturedVariables) {
    final Type type = currentType;
    final LambdaBuilder lambda =
        new LambdaBuilder(
            owner,
            String.format("Monitor %s %d:%d", metricName, line, column),
            LambdaBuilder.function(A_OBJECT_ARRAY_TYPE, type),
            capturedVariables);

    steps.add(
        renderer -> {
          owner.loadGauge(metricName, help, names, renderer.methodGen());
          lambda.push(renderer);
          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_MONITOR);
        });
    return lambda.renderer(type, this::emitSigner);
  }

  public Renderer peek(String action, int line, int column, LoadableValue[] capturedVariables) {
    final Type type = currentType;
    final LambdaBuilder lambda =
        new LambdaBuilder(
            owner,
            String.format("%s %d:%d", action, line, column),
            LambdaBuilder.consumer(type),
            capturedVariables);
    steps.add(
        renderer -> {
          lambda.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__PEEK);
        });
    return lambda.renderer(type, this::emitSigner);
  }

  /**
   * Select rows based on a minimum/maximum value for some expression in a group
   *
   * @param compareType the type that we are finding the minimum/maximum of
   * @param max whether we are finding a maximum or a minimum
   * @param discriminators the stream variables over which the groups are constructed
   * @param capturedVariables any captures that are needed by the comparison expression
   * @return a new method that must return a value to be compared for a input row
   */
  public Renderer pick(
      int line,
      int column,
      Imyhat compareType,
      boolean max,
      Stream<Target> discriminators,
      LoadableValue[] capturedVariables) {
    final Type streamType = currentType;

    final LambdaBuilder hashCodeLambda =
        new LambdaBuilder(
            owner,
            String.format("Pick %d:%d hash", line, column),
            LambdaBuilder.toIntFunction(streamType));

    final LambdaBuilder equalsLambda =
        new LambdaBuilder(
            owner,
            String.format("Pick %d:%d equals", line, column),
            LambdaBuilder.bipredicate(streamType, streamType),
            capturedVariables);

    final LambdaBuilder extractLambda =
        new LambdaBuilder(
            owner,
            String.format("Pick %d:%d 🔍", line, column),
            LambdaBuilder.function(compareType.apply(TypeUtils.TO_BOXED_ASM), streamType),
            capturedVariables);

    steps.add(
        renderer -> {
          hashCodeLambda.push(renderer);
          equalsLambda.push(renderer);
          extractLambda.push(renderer);
          renderer.invokeInterfaceStatic(A_COMPARATOR_TYPE, METHOD_COMPARATOR__COMPARING);
          if (max) {
            renderer.methodGen().invokeInterface(A_COMPARATOR_TYPE, METHOD_COMPARATOR__REVERSED);
          }
          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_PICK);
        });
    final GeneratorAdapter hashCodeGenerator = hashCodeLambda.methodGen();
    hashCodeGenerator.visitCode();
    hashCodeGenerator.push(0);

    final GeneratorAdapter equalsGenerator = equalsLambda.methodGen();
    equalsGenerator.visitCode();
    final Label end = equalsGenerator.newLabel();

    discriminators.forEach(
        discriminator -> {
          final Method getter =
              new Method(
                  discriminator.name(),
                  discriminator.type().apply(TypeUtils.TO_ASM),
                  new Type[] {});
          equalsGenerator.loadArg(0);
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(equalsGenerator);
          } else {
            equalsGenerator.invokeVirtual(streamType, getter);
          }
          equalsGenerator.loadArg(1);
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(equalsGenerator);
          } else {
            equalsGenerator.invokeVirtual(streamType, getter);
          }
          switch (discriminator.type().apply(TypeUtils.TO_ASM).getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
              equalsGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
              equalsGenerator.ifZCmp(GeneratorAdapter.EQ, end);
              break;
            default:
              equalsGenerator.ifCmp(
                  discriminator.type().apply(TypeUtils.TO_ASM), GeneratorAdapter.NE, end);
          }

          hashCodeGenerator.push(31);
          hashCodeGenerator.math(GeneratorAdapter.MUL, INT_TYPE);
          hashCodeGenerator.loadArg(0);
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(hashCodeGenerator);
          } else {
            hashCodeGenerator.invokeVirtual(streamType, getter);
          }
          switch (discriminator.type().apply(TypeUtils.TO_ASM).getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
              hashCodeGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_HASH_CODE);
              break;
            default:
              hashCodeGenerator.invokeStatic(
                  discriminator.type().apply(TypeUtils.TO_BOXED_ASM),
                  new Method(
                      "hashCode",
                      INT_TYPE,
                      new Type[] {discriminator.type().apply(TypeUtils.TO_ASM)}));
              break;
          }
          hashCodeGenerator.math(GeneratorAdapter.ADD, INT_TYPE);
        });

    hashCodeGenerator.returnValue();
    hashCodeGenerator.visitMaxs(0, 0);
    hashCodeGenerator.visitEnd();

    equalsGenerator.push(true);
    equalsGenerator.returnValue();
    equalsGenerator.mark(end);
    equalsGenerator.push(false);
    equalsGenerator.returnValue();
    equalsGenerator.visitMaxs(0, 0);
    equalsGenerator.visitEnd();

    return extractLambda.renderer(streamType, this::emitSigner);
  }

  public final RegroupVariablesBuilder regroup(
      int line, int column, LoadableValue... capturedVariables) {
    final String className = String.format("shesmu/dyn/Group_%d_%d", line, column);

    final Type oldType = currentType;
    final Type newType = Type.getObjectType(className);
    currentType = newType;

    final LambdaBuilder newLambda =
        new LambdaBuilder(
            owner,
            String.format("Group %d:%d ✨", line, column),
            LambdaBuilder.function(newType, oldType),
            capturedVariables);
    final LambdaBuilder collectLambda =
        new LambdaBuilder(
            owner,
            String.format("Group %d:%d 🧲", line, column),
            LambdaBuilder.biconsumer(newType, oldType),
            capturedVariables);

    steps.add(
        renderer -> {
          newLambda.push(renderer);
          collectLambda.push(renderer);
          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_REGROUP);

          LambdaBuilder.pushVirtual(
              renderer,
              RegroupVariablesBuilder.METHOD_IS_OK.getName(),
              LambdaBuilder.predicate(newType));
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });

    final Renderer newRenderer = newLambda.renderer(oldType, this::emitSigner);
    final Renderer collectedRenderer = collectLambda.renderer(oldType, 1, this::emitSigner);

    return new RegroupVariablesBuilder(
        owner, className, newRenderer, collectedRenderer, capturedVariables.length);
  }
}
