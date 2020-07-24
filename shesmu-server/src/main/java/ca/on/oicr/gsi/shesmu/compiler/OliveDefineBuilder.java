package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Creates bytecode for a “Define”-style olive to be used in call clauses */
public final class OliveDefineBuilder extends BaseOliveBuilder {

  public static final LoadableValue SIGNER_ACCESSOR_LOADABLE_VALUE =
      new LoadableValue() {
        @Override
        public String name() {
          return SIGNER_ACCESSOR_NAME;
        }

        @Override
        public Type type() {
          return A_SIGNATURE_ACCESSOR_TYPE;
        }

        @Override
        public void accept(Renderer renderer) {
          renderer.methodGen().loadArg(5);
        }
      };
  private final Method method;
  private final List<LoadableValue> parameters;

  private final String signerPrefix;

  public OliveDefineBuilder(RootBuilder owner, String name, Stream<? extends Target> parameters) {
    super(owner, owner.inputFormatDefinition());
    this.parameters =
        parameters
            .map(Pair.number(6))
            .map(Pair.transform(LoadParameter::new))
            .collect(Collectors.toList());
    method =
        new Method(
            String.format("Define %s", name),
            A_STREAM_TYPE,
            Stream.concat(
                    Stream.of(
                        A_STREAM_TYPE,
                        A_OLIVE_SERVICES_TYPE,
                        A_INPUT_PROVIDER_TYPE,
                        Type.INT_TYPE,
                        Type.INT_TYPE,
                        A_SIGNATURE_ACCESSOR_TYPE),
                    this.parameters.stream().map(LoadableValue::type))
                .toArray(Type[]::new));
    signerPrefix = String.format("Define %s ", name);
    owner
        .signatureVariables()
        .forEach(
            signer -> {
              owner
                  .classVisitor
                  .visitField(
                      Opcodes.ACC_PRIVATE,
                      signerPrefix + signer.name(),
                      signer.storageType().getDescriptor(),
                      null,
                      null)
                  .visitEnd();
            });
  }

  @Override
  protected void emitSigner(SignatureDefinition signer, Renderer renderer) {
    renderer.emitNamed(SIGNER_ACCESSOR_NAME);
    renderer.methodGen().push(signer.name());
    switch (signer.storage()) {
      case DYNAMIC:
        renderer.loadStream();
        renderer
            .methodGen()
            .invokeInterface(
                A_SIGNATURE_ACCESSOR_TYPE, METHOD_SIGNATURE_ACCESSOR__DYNAMIC_SIGNATURE);
        renderer.methodGen().unbox(signer.type().apply(TypeUtils.TO_ASM));
        break;
      case STATIC:
        renderer
            .methodGen()
            .invokeInterface(
                A_SIGNATURE_ACCESSOR_TYPE, METHOD_SIGNATURE_ACCESSOR__STATIC_SIGNATURE);
        renderer.methodGen().unbox(signer.type().apply(TypeUtils.TO_ASM));
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Writes the byte code for this method.
   *
   * <p>This must be called before using this in a “Call” clause.
   */
  public void finish() {
    final Renderer renderer =
        new RendererNoStream(
            owner,
            new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
            Stream.concat(parameters.stream(), Stream.of(SIGNER_ACCESSOR_LOADABLE_VALUE)),
            this::emitSigner);
    renderer.methodGen().visitCode();
    renderer.methodGen().loadArg(0);
    steps.forEach(step -> step.accept(renderer));
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  @Override
  protected void loadAccessor(Renderer renderer) {
    renderer.emitNamed(SIGNER_ACCESSOR_NAME);
  }

  @Override
  protected void loadInputProvider(GeneratorAdapter method) {
    method.loadArg(2);
  }

  @Override
  protected void loadOliveServices(GeneratorAdapter method) {
    method.loadArg(1);
  }

  @Override
  protected void loadOwnerSourceLocation(GeneratorAdapter method) {
    method.loadArg(3);
    method.loadArg(4);
  }

  @Override
  protected void loadSigner(SignatureDefinition signer, Renderer renderer) {
    final String name = signerPrefix + signer.name();
    renderer.methodGen().loadThis();
    renderer.methodGen().getField(owner.selfType(), name, signer.storageType());
  }

  @Override
  public Stream<LoadableValue> loadableValues() {
    return Stream.of(
            Stream.of(SIGNER_ACCESSOR_LOADABLE_VALUE), parameters.stream(), owner.constants(true))
        .flatMap(Function.identity());
  }

  /** The method definition for this matcher */
  public Method method() {
    return method;
  }

  /** The type of a bound parameter */
  public Type parameterType(int i) {
    return parameters.get(i).type();
  }

  /** The number of bound parameters */
  public int parameters() {
    return parameters.size();
  }
}
