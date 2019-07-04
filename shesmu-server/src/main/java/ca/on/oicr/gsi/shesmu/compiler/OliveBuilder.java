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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** An olive that will result in an action being performed */
public final class OliveBuilder extends BaseOliveBuilder {

  public static void emitAlert(
      GeneratorAdapter methodGen, int labelLocal, int annotationLocal, int ttlLocal) {
    methodGen.loadLocal(labelLocal);
    methodGen.loadLocal(annotationLocal);
    methodGen.loadLocal(ttlLocal);
    methodGen.invokeInterface(A_ACTION_CONSUMER_TYPE, METHOD_ACTION_CONSUMER__ACCEPT_ALERT);
    methodGen.pop();
  }

  private static final Type A_ACTION_CONSUMER_TYPE = Type.getType(OliveServices.class);
  private static final Type A_ACTION_TYPE = Type.getType(Action.class);
  private static final Type A_REFILLER_TYPE = Type.getType(Refiller.class);
  private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  private static final Type A_SYSTEM_TYPE = Type.getType(System.class);
  protected static final Handle LAMBDA_METAFACTORY_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(LambdaMetafactory.class).getInternalName(),
          "metafactory",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
          false);
  private static final Method METHOD_ACTION_CONSUMER__ACCEPT_ACTION =
      new Method(
          "accept",
          BOOLEAN_TYPE,
          new Type[] {
            A_ACTION_TYPE, A_STRING_TYPE, INT_TYPE, INT_TYPE, LONG_TYPE, A_STRING_ARRAY_TYPE
          });
  private static final Method METHOD_ACTION_CONSUMER__ACCEPT_ALERT =
      new Method(
          "accept", BOOLEAN_TYPE, new Type[] {A_STRING_ARRAY_TYPE, A_STRING_ARRAY_TYPE, LONG_TYPE});
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
  private final int column;
  private final int line;
  private final String signerPrefix;

  public OliveBuilder(
      RootBuilder owner,
      InputFormatDefinition initialFormat,
      int line,
      int column,
      Stream<? extends Target> signableNames) {
    super(owner, initialFormat);
    this.line = line;
    this.column = column;
    signerPrefix = String.format("Olive %d:%d ", line, column);
    final List<Target> signables = signableNames.collect(Collectors.toList());
    owner
        .signatureVariables()
        .forEach(
            signer -> {
              final String name = signerPrefix + signer.name();
              switch (signer.storage()) {
                case STATIC:
                  owner.classVisitor.visitField(
                      Opcodes.ACC_STATIC,
                      name,
                      signer.type().apply(TypeUtils.TO_ASM).getDescriptor(),
                      null,
                      null);
                  signer.build(owner.classInitMethod, initialFormat.type(), signables.stream());
                  owner.classInitMethod.putStatic(
                      owner.selfType(), name, signer.type().apply(TypeUtils.TO_ASM));
                  break;
                case DYNAMIC:
                  final Method method =
                      new Method(
                          name,
                          signer.type().apply(TypeUtils.TO_ASM),
                          new Type[] {initialFormat.type()});
                  final GeneratorAdapter methodGen =
                      new GeneratorAdapter(
                          Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                          method,
                          null,
                          null,
                          owner.classVisitor);
                  methodGen.visitCode();
                  signer.build(methodGen, initialFormat.type(), signables.stream());
                  methodGen.returnValue();
                  methodGen.visitMaxs(0, 0);
                  methodGen.visitEnd();
                  break;
                default:
                  throw new UnsupportedOperationException();
              }
            });
  }

  /**
   * Run an action
   *
   * <p>Consume an action from the stack and queue to be executed by the server
   *
   * @param methodGen the method generator, which must be the method generator produced by {@link
   *     #finish(String,Stream)}
   */
  public void emitAction(GeneratorAdapter methodGen, int local, Set<String> tags) {
    methodGen.loadArg(0);
    methodGen.loadLocal(local);
    methodGen.push(owner.sourcePath());
    methodGen.push(line);
    methodGen.push(column);
    methodGen.push(owner.compileTime);
    methodGen.push(tags.size());
    methodGen.newArray(A_STRING_TYPE);
    int tagIndex = 0;
    for (final String tag : tags) {
      methodGen.dup();
      methodGen.push(tagIndex++);
      methodGen.push(tag);
      methodGen.arrayStore(A_STRING_TYPE);
    }
    methodGen.invokeInterface(A_ACTION_CONSUMER_TYPE, METHOD_ACTION_CONSUMER__ACCEPT_ACTION);
    methodGen.pop();
  }

  @Override
  protected void emitSigner(SignatureDefinition signer, Renderer renderer) {
    switch (signer.storage()) {
      case DYNAMIC:
        renderer.loadStream();
        renderer
            .methodGen()
            .invokeStatic(
                owner.selfType(),
                new Method(
                    signerPrefix + signer.name(),
                    signer.type().apply(TypeUtils.TO_ASM),
                    new Type[] {initialFormat.type()}));
        break;
      case STATIC:
        renderer
            .methodGen()
            .getStatic(
                owner.selfType(),
                signerPrefix + signer.name(),
                signer.type().apply(TypeUtils.TO_ASM));
        break;
      default:
        throw new UnsupportedOperationException();
    }
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
