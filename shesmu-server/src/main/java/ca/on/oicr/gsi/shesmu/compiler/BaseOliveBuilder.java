package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.SignatureAccessor;
import io.prometheus.client.Gauge;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
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
  private static final Type A_GROUPER_TYPE = Type.getType(Grouper.class);
  protected static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  private static final Type A_OBJECTS_TYPE = Type.getType(Objects.class);
  private static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
  protected static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  protected static final Type A_OLIVE_SERVICES_TYPE = Type.getType(OliveServices.class);
  private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  protected static final Type A_SIGNATURE_ACCESSOR_TYPE = Type.getType(SignatureAccessor.class);
  protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);
  private static final Handle GROUPER_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(GrouperDefinition.class).getInternalName(),
          "bootstrap",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
          false);
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
  private static final Method METHOD_LEFT_INTERSECTION_JOIN =
      new Method(
          "leftIntersectionJoin",
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
  private static final Method METHOD_REGROUP_WITH_GROUPER =
      new Method(
          "regroup", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE, A_GROUPER_TYPE, A_FUNCTION_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__FLATTEN =
      new Method(
          "flatten", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE, A_FUNCTION_TYPE, A_BIFUNCTION_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__JOIN =
      new Method(
          "join",
          A_STREAM_TYPE,
          new Type[] {
            A_STREAM_TYPE, A_STREAM_TYPE, A_FUNCTION_TYPE, A_FUNCTION_TYPE, A_BIFUNCTION_TYPE
          });
  private static final Method METHOD_RUNTIME_SUPPORT__JOIN_INTERSECTION =
      new Method(
          "joinIntersection",
          A_STREAM_TYPE,
          new Type[] {
            A_STREAM_TYPE, A_STREAM_TYPE, A_FUNCTION_TYPE, A_FUNCTION_TYPE, A_BIFUNCTION_TYPE
          });
  protected static final Method METHOD_SIGNATURE_ACCESSOR__DYNAMIC_SIGNATURE =
      new Method("dynamicSignature", A_OBJECT_TYPE, new Type[] {A_STRING_TYPE, A_OBJECT_TYPE});
  protected static final Method METHOD_SIGNATURE_ACCESSOR__STATIC_SIGNATURE =
      new Method("staticSignature", A_OBJECT_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method METHOD_STREAM__FILTER =
      new Method("filter", A_STREAM_TYPE, new Type[] {A_PREDICATE_TYPE});
  private static final Method METHOD_STREAM__MAP =
      new Method("map", A_STREAM_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_STREAM__PEEK =
      new Method("peek", A_STREAM_TYPE, new Type[] {A_CONSUMER_TYPE});
  public static final String SIGNER_ACCESSOR_NAME = "Signer Accessor";

  public static void createSignatureInfrastructure(
      RootBuilder owner,
      String prefix,
      InputFormatDefinition inputFormat,
      List<SignableRenderer> signables,
      SignatureDefinition signer) {
    final String name = prefix + signer.name();
    switch (signer.storage()) {
      case STATIC:
        owner.classVisitor.visitField(
            Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC,
            name,
            signer.type().apply(TypeUtils.TO_ASM).getDescriptor(),
            null,
            null);
        signer.build(owner.classInitMethod, inputFormat.type(), signables.stream());
        owner.classInitMethod.putStatic(
            owner.selfType(), name, signer.type().apply(TypeUtils.TO_ASM));
        break;
      case DYNAMIC:
        final Method method =
            new Method(
                name, signer.type().apply(TypeUtils.TO_ASM), new Type[] {inputFormat.type()});
        final GeneratorAdapter methodGen =
            new GeneratorAdapter(
                Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, method, null, null, owner.classVisitor);
        methodGen.visitCode();
        signer.build(methodGen, inputFormat.type(), signables.stream());
        methodGen.returnValue();
        methodGen.visitMaxs(0, 0);
        methodGen.visitEnd();
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

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
  public final void call(
      CallableDefinitionRenderer defineOlive, Stream<Consumer<Renderer>> arguments) {
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
          defineOlive.generatePreamble(renderer.methodGen());
          loadOliveServices(renderer.methodGen());
          loadInputProvider(renderer.methodGen());
          loadOwnerSourceLocation(renderer.methodGen());
          loadAccessor(renderer);
          for (int i = 0; i < arglist.size(); i++) {
            arglist.get(i).accept(renderer);
          }
          defineOlive.generateCall(renderer.methodGen());
        });
    currentType = defineOlive.currentType();
  }

  public final void createSignature(
      String prefix,
      InputFormatDefinition inputFormat,
      Stream<SignableRenderer> signables,
      SignatureDefinition signer) {
    createSignatureInfrastructure(owner, prefix, inputFormat, signables.collect(Collectors.toList()), signer);
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

  public void filterNonNull() {
    steps.add(
        renderer -> {
          LambdaBuilder.pushStatic(
              renderer, A_OBJECTS_TYPE, "nonNull", LambdaBuilder.predicate(A_OBJECT_TYPE));
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });
  }

  public FlattenBuilder flatten(
      int line,
      int column,
      Imyhat unrollType,
      boolean copySignatures,
      LoadableValue... capturedVariables) {
    final String className = String.format("shesmu/dyn/Flatten %d:%d", line, column);

    final Type oldType = currentType;
    final Type newType = Type.getObjectType(className);
    currentType = newType;

    final LambdaBuilder explodeLambda =
        new LambdaBuilder(
            owner,
            String.format("Flatten %d:%d 🧨", line, column),
            LambdaBuilder.function(A_STREAM_TYPE, oldType),
            capturedVariables);

    final FlattenBuilder flattenBuilder =
        new FlattenBuilder(
            owner,
            newType,
            oldType,
            unrollType.apply(TO_ASM),
            copySignatures,
            explodeLambda.renderer(oldType, this::emitSigner));

    final Consumer<Renderer> pushConstructor;
    if (copySignatures) {
      final LambdaBuilder constructLambda =
          new LambdaBuilder(
              owner,
              String.format("Flatten %d:%d✨", line, column),
              LambdaBuilder.bifunction(newType, oldType, unrollType));

      final Renderer constructorRenderer = constructLambda.renderer(oldType, 0, this::emitSigner);
      constructorRenderer.methodGen().visitCode();
      constructorRenderer.methodGen().newInstance(flattenBuilder.type());
      constructorRenderer.methodGen().dup();
      constructorRenderer.methodGen().loadArg(constructLambda.trueArgument(0));
      constructorRenderer.methodGen().loadArg(constructLambda.trueArgument(1));
      owner.signatureVariables().forEach(constructorRenderer::emitSigner);
      constructorRenderer
          .methodGen()
          .invokeConstructor(flattenBuilder.type(), flattenBuilder.constructor());
      constructorRenderer.methodGen().returnValue();

      constructorRenderer.methodGen().endMethod();
      pushConstructor = constructLambda::push;
    } else {
      pushConstructor =
          render ->
              LambdaBuilder.pushNew(render, LambdaBuilder.bifunction(newType, oldType, unrollType));
    }

    steps.add(
        renderer -> {
          explodeLambda.push(renderer);
          pushConstructor.accept(renderer);
          renderer
              .methodGen()
              .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__FLATTEN);
        });

    return flattenBuilder;
  }

  public final JoinBuilder join(
      int line,
      int column,
      boolean intersection,
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

          renderer
              .methodGen()
              .invokeStatic(
                  A_RUNTIME_SUPPORT_TYPE,
                  intersection
                      ? METHOD_RUNTIME_SUPPORT__JOIN_INTERSECTION
                      : METHOD_RUNTIME_SUPPORT__JOIN);
        });

    final Renderer outerKeyMethodGen = outerKeyLambda.renderer(oldType, this::emitSigner);
    final Renderer innerKeyMethodGen = innerKeyLambda.renderer(innerType.type(), this::emitSigner);
    return new JoinBuilder(
        owner, newType, oldType, innerType.type(), outerKeyMethodGen, innerKeyMethodGen);
  }

  public final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin(
      int line,
      int column,
      boolean intersection,
      InputFormatDefinition innerType,
      Imyhat keyType,
      BiConsumer<SignatureDefinition, Renderer> innerSigner,
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

          renderer
              .methodGen()
              .invokeStatic(
                  A_RUNTIME_SUPPORT_TYPE,
                  intersection ? METHOD_LEFT_INTERSECTION_JOIN : METHOD_LEFT_JOIN);

          LambdaBuilder.pushVirtual(
              renderer,
              RegroupVariablesBuilder.METHOD_IS_OK.getName(),
              LambdaBuilder.predicate(newType));
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });

    final Renderer newMethodGen = newMethod.renderer(joinedType, this::emitSigner);
    final Renderer outerKeyMethodGen = outerKeyLambda.renderer(oldType, this::emitSigner);
    final Renderer innerKeyMethodGen = innerKeyLambda.renderer(innerType.type(), innerSigner);
    final Renderer collectedMethodGen = collectLambda.renderer(joinedType, 1, innerSigner);

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

  protected abstract void loadAccessor(Renderer renderer);

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
              new Method(discriminator.name(), discriminator.type().apply(TO_ASM), new Type[] {});
          equalsGenerator.loadArg(equalsLambda.trueArgument(0));
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(equalsGenerator);
          } else {
            equalsGenerator.invokeVirtual(streamType, getter);
          }
          equalsGenerator.loadArg(equalsLambda.trueArgument(1));
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(equalsGenerator);
          } else {
            equalsGenerator.invokeVirtual(streamType, getter);
          }
          switch (discriminator.type().apply(TO_ASM).getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
              equalsGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
              equalsGenerator.ifZCmp(GeneratorAdapter.EQ, end);
              break;
            default:
              equalsGenerator.ifCmp(discriminator.type().apply(TO_ASM), GeneratorAdapter.NE, end);
          }

          hashCodeGenerator.push(31);
          hashCodeGenerator.math(GeneratorAdapter.MUL, INT_TYPE);
          hashCodeGenerator.loadArg(hashCodeLambda.trueArgument(0));
          if (discriminator instanceof InputVariable) {
            ((InputVariable) discriminator).extract(hashCodeGenerator);
          } else {
            hashCodeGenerator.invokeVirtual(streamType, getter);
          }
          switch (discriminator.type().apply(TO_ASM).getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
              hashCodeGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_HASH_CODE);
              break;
            default:
              hashCodeGenerator.invokeStatic(
                  discriminator.type().apply(TypeUtils.TO_BOXED_ASM),
                  new Method(
                      "hashCode", INT_TYPE, new Type[] {discriminator.type().apply(TO_ASM)}));
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

  /**
   * Create a regrouping operation that uses a grouper to create subgroups. This mostly looks like a
   * regular grouping operation, but after the grouping, the grouper will be invoked (possibly many
   * times) with the function that can capture additional info as required by the grouping
   * operation.
   *
   * @param line source line
   * @param column source column
   * @param collectorBuilderType the type of the intermediate lambda. This is the final argument to
   *     the grouper constructor, which must be some kind of lambda for any of this to make sense
   * @param grouperCaptures the captures (fixed parameters) belonging to the grouper; the type
   *     provided is null if this is a real value (and its type can be inferred from the lambda
   *     type). If provided the lambda should return a function and this is the return type of that
   *     function.
   * @param capturedVariables the variables that will be captured; this does not include the
   *     additional captures provided by the grouper.
   */
  public final RegroupVariablesBuilder regroupWithGrouper(
      int line,
      int column,
      String grouperName,
      LambdaBuilder.LambdaType collectorBuilderType,
      LoadableValue[] grouperCaptures,
      List<Pair<String, Type>> grouperVariables,
      LoadableValue... capturedVariables) {
    final String className = String.format("shesmu/dyn/GroupWithGrouper_%d_%d", line, column);
    final Type oldType = currentType;
    final Type newType = Type.getObjectType(className);
    currentType = newType;

    final LambdaBuilder newLambda =
        new LambdaBuilder(
            owner,
            String.format("Group with Grouper %d:%d ✨", line, column),
            LambdaBuilder.function(newType, oldType),
            capturedVariables);

    // Since every grouper can apply extra arguments, we need to prepare a lambda that takes
    // those extra arguments and captures them into a final BiConsumer that the wrapper can use to
    // collect the values. This is that capturing method. It first captures all the external values
    // into a type specified by the grouper. Inside it creates a new lambda capturing the external
    // values plus the new ones provided by the grouper.
    final LambdaBuilder collectBuilderLambda =
        new LambdaBuilder(
            owner,
            String.format("Group with Grouper %d:%d 🔨", line, column),
            collectorBuilderType,
            capturedVariables);

    final Renderer collectBuilder = collectBuilderLambda.renderer(null, this::emitSigner);

    final LambdaBuilder collectLambda =
        new LambdaBuilder(
            owner,
            String.format("Group with Grouper %d:%d 🧲", line, column),
            LambdaBuilder.biconsumer(newType, oldType),
            Stream.concat(
                    Stream.of(capturedVariables)
                        .map(original -> collectBuilder.getNamed(original.name())),
                    collectorBuilderType
                        .parameterTypes(LambdaBuilder.AccessMode.REAL)
                        .map(
                            new Function<Type, LoadableValue>() {
                              int counter;

                              @Override
                              public LoadableValue apply(Type type) {
                                final int index = counter++;
                                return new LoadableValue() {
                                  @Override
                                  public void accept(Renderer renderer) {
                                    renderer.methodGen().loadArg(capturedVariables.length + index);
                                  }

                                  @Override
                                  public String name() {
                                    return grouperVariables.get(index).first();
                                  }

                                  @Override
                                  public Type type() {
                                    return type;
                                  }
                                };
                              }
                            }))
                .toArray(LoadableValue[]::new));

    collectBuilder.methodGen().visitCode();
    collectLambda.push(collectBuilder);
    collectBuilder.methodGen().returnValue();
    collectBuilder.methodGen().visitCode();
    collectBuilder.methodGen().endMethod();

    steps.add(
        renderer -> {
          for (final LoadableValue value : grouperCaptures) {
            value.accept(renderer);
          }
          collectBuilderLambda.push(renderer);
          renderer
              .methodGen()
              .invokeDynamic(
                  grouperName,
                  Type.getMethodDescriptor(
                      A_GROUPER_TYPE,
                      Stream.concat(
                              Stream.of(grouperCaptures).map(LoadableValue::type),
                              Stream.of(collectorBuilderType.interfaceType()))
                          .toArray(Type[]::new)),
                  GROUPER_BSM);
          newLambda.push(renderer);
          renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_REGROUP_WITH_GROUPER);

          LambdaBuilder.pushVirtual(
              renderer,
              RegroupVariablesBuilder.METHOD_IS_OK.getName(),
              LambdaBuilder.predicate(newType));
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
        });

    final Renderer newRenderer = newLambda.renderer(oldType, this::emitSigner);
    final Renderer collectedRenderer = collectLambda.renderer(oldType, 1, this::emitSigner);
    // For any grouper variable that is really a function, redefine it to be the correct function
    // call followed by an unboxing
    for (final Pair<String, Type> grouperVariable : grouperVariables) {
      final Type type = grouperVariable.second();
      if (type != null) {
        final LoadableValue function = collectedRenderer.getNamed(grouperVariable.first());
        collectedRenderer.define(
            grouperVariable.first(),
            new LoadableValue() {
              @Override
              public void accept(Renderer renderer) {
                function.accept(renderer);
                renderer.loadStream();
                renderer.methodGen().invokeInterface(A_FUNCTION_TYPE, METHOD_FUNCTION__APPLY);
                renderer.methodGen().unbox(type);
              }

              @Override
              public String name() {
                return function.name();
              }

              @Override
              public Type type() {
                return type;
              }
            });
      }
    }

    return new RegroupVariablesBuilder(
        owner,
        className,
        newRenderer,
        collectedRenderer,
        capturedVariables.length + collectorBuilderType.parameters());
  }

  public static void renderSigner(
      RootBuilder owner,
      InputFormatDefinition format,
      String prefix,
      SignatureDefinition signer,
      Renderer renderer) {
    switch (signer.storage()) {
      case DYNAMIC:
        renderer.loadStream();
        renderer
            .methodGen()
            .invokeStatic(
                owner.selfType(),
                new Method(
                    prefix + signer.name(),
                    signer.type().apply(TypeUtils.TO_ASM),
                    new Type[] {format.type()}));
        break;
      case STATIC:
        renderer
            .methodGen()
            .getStatic(
                owner.selfType(), prefix + signer.name(), signer.type().apply(TypeUtils.TO_ASM));
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }
}
