package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import java.lang.invoke.LambdaMetafactory;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** An olive that will result in an action being performed */
public final class OliveBuilder extends BaseOliveBuilder {

  private static final Type A_ACTION_CONSUMER_TYPE = Type.getType(OliveServices.class);
  private static final Type A_ACTION_TYPE = Type.getType(Action.class);
  private static final Type A_REFILLER_TYPE = Type.getType(Refiller.class);
  private static final Type A_RUNTIME_EXCEPTION_TYPE = Type.getType(RuntimeException.class);
  private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  private static final Type A_SYSTEM_TYPE = Type.getType(System.class);
  protected static final Handle LAMBDA_METAFACTORY_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(LambdaMetafactory.class).getInternalName(),
          "metafactory",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
          false);
  private static final Method METHOD_OBJECT__EQUALS =
      new Method("equals", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_OLIVE_SERVICES__ACCEPT_ACTION =
      new Method(
          "accept",
          BOOLEAN_TYPE,
          new Type[] {
            A_ACTION_TYPE, A_STRING_TYPE, INT_TYPE, INT_TYPE, A_STRING_TYPE, A_STRING_ARRAY_TYPE
          });
  private static final Method METHOD_OLIVE_SERVICES__ACCEPT_ALERT =
      new Method(
          "accept",
          BOOLEAN_TYPE,
          new Type[] {
            A_STRING_ARRAY_TYPE,
            A_STRING_ARRAY_TYPE,
            LONG_TYPE,
            A_STRING_TYPE,
            INT_TYPE,
            INT_TYPE,
            A_STRING_TYPE
          });
  private static final Method METHOD_OLIVE_SERVICES__OLIVE_RUNTIME =
      new Method(
          "oliveRuntime", VOID_TYPE, new Type[] {A_STRING_TYPE, INT_TYPE, INT_TYPE, LONG_TYPE});
  private static final Method METHOD_REFILLER__CONSUME =
      new Method("consume", VOID_TYPE, new Type[] {A_STREAM_TYPE});
  private static final Method METHOD_STREAM__CLOSE = new Method("close", VOID_TYPE, new Type[] {});
  private static final Method METHOD_STREAM__FOR_EACH =
      new Method("forEach", VOID_TYPE, new Type[] {A_CONSUMER_TYPE});
  private static final Method METHOD_SYSTEM__NANO_TIME =
      new Method("nanoTime", LONG_TYPE, new Type[] {});

  public static void emitAlert(
      GeneratorAdapter methodGen,
      int labelLocal,
      int annotationLocal,
      int ttlLocal,
      String filename,
      int column,
      int line,
      String hash) {
    methodGen.loadLocal(labelLocal);
    methodGen.loadLocal(annotationLocal);
    methodGen.loadLocal(ttlLocal);
    methodGen.push(filename);
    methodGen.push(column);
    methodGen.push(line);
    methodGen.push(hash);
    methodGen.invokeInterface(A_ACTION_CONSUMER_TYPE, METHOD_OLIVE_SERVICES__ACCEPT_ALERT);
    methodGen.pop();
  }

  private final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});
  private final Type accessorType;
  private final int column;
  private boolean hasAccessor;
  private final int line;
  private final String signerPrefix;

  public OliveBuilder(
      RootBuilder owner,
      InputFormatDefinition initialFormat,
      int line,
      int column,
      Stream<SignableRenderer> signableNames) {
    super(owner, initialFormat);
    this.line = line;
    this.column = column;
    accessorType =
        Type.getObjectType(String.format("shesmu/dyn/Signer Accessor %d:%d", line, column));
    signerPrefix = String.format("Olive %d:%d ", line, column);
    final List<SignableRenderer> signables = signableNames.collect(Collectors.toList());
    owner
        .signatureVariables()
        .forEach(
            signer -> createSignature(signerPrefix, initialFormat, signables.stream(), signer));
  }

  /**
   * Run an action
   *
   * <p>Consume an action from the stack and queue to be executed by the server
   *
   * @param methodGen the method generator, which must be the method generator produced by {@link
   *     #finish(String,Stream)}
   */
  public void emitAction(GeneratorAdapter methodGen, int local, int tags) {
    methodGen.loadArg(0);
    methodGen.loadLocal(local);
    methodGen.push(owner.sourcePath());
    methodGen.push(line);
    methodGen.push(column);
    methodGen.push(owner.hash);
    methodGen.loadLocal(tags);
    methodGen.invokeInterface(A_ACTION_CONSUMER_TYPE, METHOD_OLIVE_SERVICES__ACCEPT_ACTION);
    methodGen.pop();
  }

  @Override
  protected void emitSigner(SignatureDefinition signer, Renderer renderer) {
    renderSigner(owner, initialFormat, signerPrefix, signer, renderer);
  }

  private void finish(Consumer<Renderer> finishStream) {
    final Renderer runMethod = owner.rootRenderer(true);
    final int startTime = runMethod.methodGen().newLocal(LONG_TYPE);
    runMethod.methodGen().invokeStatic(A_SYSTEM_TYPE, METHOD_SYSTEM__NANO_TIME);
    runMethod.methodGen().storeLocal(startTime);

    runMethod.methodGen().loadArg(1);
    runMethod.methodGen().push(initialFormat.name());
    runMethod.methodGen().invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);

    steps.forEach(step -> step.accept(owner.rootRenderer(true)));

    runMethod.methodGen().dup();
    finishStream.accept(runMethod);

    runMethod.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__CLOSE);

    runMethod.methodGen().loadArg(0);
    runMethod.methodGen().push(owner.sourcePath());
    runMethod.methodGen().push(line);
    runMethod.methodGen().push(column);
    runMethod.methodGen().invokeStatic(A_SYSTEM_TYPE, METHOD_SYSTEM__NANO_TIME);
    runMethod.methodGen().loadLocal(startTime);
    runMethod.methodGen().math(GeneratorAdapter.SUB, LONG_TYPE);
    runMethod
        .methodGen()
        .invokeInterface(A_OLIVE_SERVICES_TYPE, METHOD_OLIVE_SERVICES__OLIVE_RUNTIME);
  }

  /** Generate bytecode for the olive and create a method to consume the result. */
  public final Renderer finish(String actionName, Stream<LoadableValue> captures) {
    final LambdaBuilder consumer =
        new LambdaBuilder(
            owner,
            String.format("%s %d:%d", actionName, line, column),
            LambdaBuilder.consumer(currentType()),
            Stream.concat(
                    Stream.of(
                        new LoadableValue() {
                          @Override
                          public void accept(Renderer renderer) {
                            renderer.methodGen().loadArg(0);
                          }

                          @Override
                          public String name() {
                            return "Olive Services";
                          }

                          @Override
                          public Type type() {
                            return A_ACTION_CONSUMER_TYPE;
                          }
                        }),
                    captures)
                .toArray(LoadableValue[]::new));
    finish(
        runMethod -> {
          consumer.push(runMethod);
          runMethod.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FOR_EACH);
        });

    return consumer.renderer(currentType(), this::emitSigner);
  }

  public final void finish(
      String refillerName, Consumer<Renderer> creator, Stream<RefillerParameterBuilder> arguments) {
    finish(
        runMethod -> {
          creator.accept(runMethod);
          final int refillerLocal = runMethod.methodGen().newLocal(A_REFILLER_TYPE);
          final int functionLocal = runMethod.methodGen().newLocal(A_FUNCTION_TYPE);
          runMethod.methodGen().storeLocal(refillerLocal);
          arguments.forEach(
              argument -> {
                final LambdaBuilder function =
                    new LambdaBuilder(
                        owner,
                        String.format(
                            "%s %s %d:%d", refillerName, argument.parameter().name(), line, column),
                        LambdaBuilder.function(argument.parameter().type(), currentType()),
                        argument.captures());
                function.push(runMethod);
                runMethod.methodGen().storeLocal(functionLocal);
                argument.parameter().render(runMethod, refillerLocal, functionLocal);
                argument.render(function.renderer(currentType(), this::emitSigner));
              });
          runMethod.methodGen().loadLocal(refillerLocal);
          runMethod.methodGen().swap();
          runMethod.methodGen().invokeVirtual(A_REFILLER_TYPE, METHOD_REFILLER__CONSUME);
        });
  }

  @Override
  protected void loadAccessor(Renderer renderer) {
    if (!hasAccessor) {
      final ClassVisitor signerClass = owner.createClassVisitor();
      signerClass.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC,
          accessorType.getInternalName(),
          null,
          A_OBJECT_TYPE.getInternalName(),
          new String[] {A_SIGNATURE_ACCESSOR_TYPE.getInternalName()});

      final GeneratorAdapter ctor =
          new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_DEFAULT, null, null, signerClass);
      ctor.visitCode();
      ctor.loadThis();
      ctor.invokeConstructor(A_OBJECT_TYPE, CTOR_DEFAULT);
      ctor.visitInsn(Opcodes.RETURN);
      ctor.endMethod();

      final GeneratorAdapter dynamicMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              METHOD_SIGNATURE_ACCESSOR__DYNAMIC_SIGNATURE,
              null,
              null,
              signerClass);
      dynamicMethod.visitCode();
      final GeneratorAdapter staticMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              METHOD_SIGNATURE_ACCESSOR__STATIC_SIGNATURE,
              null,
              null,
              signerClass);
      staticMethod.visitCode();
      owner
          .signatureVariables()
          .forEach(
              signer -> {
                final Type resultType = signer.type().apply(TypeUtils.TO_ASM);
                switch (signer.storage()) {
                  case STATIC:
                    final Label staticNext = staticMethod.newLabel();
                    staticMethod.loadArg(0);
                    staticMethod.push(signer.name());
                    staticMethod.invokeVirtual(A_STRING_TYPE, METHOD_OBJECT__EQUALS);
                    staticMethod.ifZCmp(GeneratorAdapter.EQ, staticNext);
                    staticMethod.getStatic(
                        owner.selfType(), signerPrefix + signer.name(), resultType);
                    staticMethod.valueOf(resultType);
                    staticMethod.returnValue();
                    staticMethod.mark(staticNext);
                    break;
                  case DYNAMIC:
                    final Label dynamicNext = dynamicMethod.newLabel();
                    dynamicMethod.loadArg(0);
                    dynamicMethod.push(signer.name());
                    dynamicMethod.invokeVirtual(A_STRING_TYPE, METHOD_OBJECT__EQUALS);
                    dynamicMethod.ifZCmp(GeneratorAdapter.EQ, dynamicNext);
                    dynamicMethod.loadArg(1);
                    dynamicMethod.unbox(initialFormat.type());
                    dynamicMethod.invokeStatic(
                        owner.selfType(),
                        new Method(
                            signerPrefix + signer.name(),
                            signer.type().apply(TypeUtils.TO_ASM),
                            new Type[] {initialFormat.type()}));
                    dynamicMethod.valueOf(resultType);
                    dynamicMethod.returnValue();
                    dynamicMethod.mark(dynamicNext);
                    break;
                }
              });

      dynamicMethod.throwException(
          A_RUNTIME_EXCEPTION_TYPE, "Unknown signer; this olive probably needs recompilation.");
      dynamicMethod.endMethod();

      staticMethod.throwException(
          A_RUNTIME_EXCEPTION_TYPE, "Unknown signer; this olive probably needs recompilation.");
      staticMethod.endMethod();

      signerClass.visitEnd();
      hasAccessor = true;
    }
    renderer.methodGen().newInstance(accessorType);
    renderer.methodGen().dup();
    renderer.methodGen().invokeConstructor(accessorType, CTOR_DEFAULT);
  }

  @Override
  protected void loadInputProvider(GeneratorAdapter method) {
    method.loadArg(1);
  }

  @Override
  protected void loadOliveServices(GeneratorAdapter method) {
    method.loadArg(0);
  }

  @Override
  protected void loadOwnerSourceLocation(GeneratorAdapter method) {
    method.push(line);
    method.push(column);
  }

  @Override
  protected void loadSigner(SignatureDefinition signer, Renderer renderer) {
    switch (signer.storage()) {
      case STATIC:
        renderer
            .methodGen()
            .getStatic(
                owner.selfType(),
                signerPrefix + signer.name(),
                signer.type().apply(TypeUtils.TO_ASM));
        break;
      case DYNAMIC:
        final Handle handle =
            new Handle(
                Opcodes.H_INVOKESTATIC,
                owner.selfType().getInternalName(),
                signerPrefix + signer.name(),
                Type.getMethodDescriptor(
                    signer.type().apply(TypeUtils.TO_ASM), initialFormat.type()),
                false);
        renderer
            .methodGen()
            .invokeDynamic(
                "apply",
                Type.getMethodDescriptor(A_FUNCTION_TYPE),
                LAMBDA_METAFACTORY_BSM,
                Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE),
                handle,
                Type.getMethodType(signer.type().apply(TypeUtils.TO_ASM), initialFormat.type()));

        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public Stream<LoadableValue> loadableValues() {
    return owner.constants(true);
  }
}
