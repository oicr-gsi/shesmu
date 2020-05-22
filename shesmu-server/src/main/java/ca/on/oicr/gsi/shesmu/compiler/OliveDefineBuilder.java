package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Creates bytecode for a “Define”-style olive to be used in call clauses */
public final class OliveDefineBuilder extends BaseOliveBuilder
    implements CallableDefinitionRenderer {

  public static final LoadableValue SIGNER_ACCESSOR_LOADABLE_VALUE =
      new LoadableValue() {
        @Override
        public void accept(Renderer renderer) {
          renderer.methodGen().loadArg(5);
        }

        @Override
        public String name() {
          return SIGNER_ACCESSOR_NAME;
        }

        @Override
        public Type type() {
          return A_SIGNATURE_ACCESSOR_TYPE;
        }
      };
  private final Method method;
  private final String name;
  private final List<LoadableValue> parameters;

  private final String signerPrefix;

  public OliveDefineBuilder(RootBuilder owner, String name, Stream<? extends Target> parameters) {
    super(owner, owner.inputFormatDefinition());
    this.name = name;
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
  }

  @Override
  public Type currentType() {
    return super.currentType();
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
        renderer.methodGen().unbox(signer.type().apply(TO_ASM));
        break;
      case STATIC:
        renderer
            .methodGen()
            .invokeInterface(
                A_SIGNATURE_ACCESSOR_TYPE, METHOD_SIGNATURE_ACCESSOR__STATIC_SIGNATURE);
        renderer.methodGen().unbox(signer.type().apply(TO_ASM));
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  public void export(Stream<? extends Target> variables, Set<String> signedVariables) {
    // If we are exporting this, we need to generate getters for each variable since we may be
    // dealing with a slightly transformed stream and the consumer of this isn't going to have
    // access to that type information
    variables.forEach(
        variable -> {
          final GeneratorAdapter getter =
              new GeneratorAdapter(
                  Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                  new Method(
                      method.getName() + " " + variable.name(),
                      Type.getMethodDescriptor(variable.type().apply(TO_ASM), A_OBJECT_TYPE)),
                  null,
                  null,
                  owner.classVisitor);
          getter.visitCode();
          getter.loadArg(0);
          getter.checkCast(currentType());
          if (variable instanceof InputVariable) {
            ((InputVariable) variable).extract(getter);
          } else {
            getter.invokeVirtual(
                currentType(),
                new Method(
                    variable.name(), variable.type().apply(TypeUtils.TO_ASM), new Type[] {}));
          }
          getter.returnValue();
          getter.endMethod();
          final GeneratorAdapter signerCheck =
              new GeneratorAdapter(
                  Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                  new Method(
                      String.format("%s %s Signer Check", method.getName(), variable.name()),
                      Type.getMethodDescriptor(Type.BOOLEAN_TYPE)),
                  null,
                  null,
                  owner.classVisitor);
          signerCheck.visitCode();
          signerCheck.push(signedVariables.contains(variable.name()));
          signerCheck.returnValue();
          signerCheck.endMethod();
        });
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
            new GeneratorAdapter(Opcodes.ACC_PUBLIC, method, null, null, owner.classVisitor),
            Stream.concat(parameters.stream(), Stream.of(SIGNER_ACCESSOR_LOADABLE_VALUE)),
            this::emitSigner);
    renderer.methodGen().visitCode();
    renderer.methodGen().loadArg(0);
    steps.forEach(step -> step.accept(renderer));
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  /** The method definition for this matcher */
  @Override
  public void generateCall(GeneratorAdapter methodGen) {
    methodGen.invokeVirtual(owner.selfType(), method);
  }

  @Override
  public void generatePreamble(GeneratorAdapter methodGen) {
    methodGen.loadThis();
    methodGen.swap();
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

  @Override
  public String name() {
    return name;
  }

  /** The type of a bound parameter */
  @Override
  public Type parameter(int i) {
    return parameters.get(i).type();
  }

  /** The number of bound parameters */
  @Override
  public int parameters() {
    return parameters.size();
  }
}
