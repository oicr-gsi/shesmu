package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.SignableRenderer;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Create a signature variable for a subclass of {@link DynamicSigner} */
public abstract class SignatureVariableForDynamicSigner extends SignatureDefinition {
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_DYNAMIC_SIGNER_TYPE = Type.getType(DynamicSigner.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Method METHOD_DYNAMIC_SIGNER__ADD_VARIABLE =
      new Method(
          "addVariable", Type.VOID_TYPE, new Type[] {A_STRING_TYPE, A_IMYHAT_TYPE, A_OBJECT_TYPE});

  private static final Method METHOD_DYNAMIC_SIGNER__FINISH =
      new Method("finish", A_OBJECT_TYPE, new Type[] {});

  public SignatureVariableForDynamicSigner(String name, Imyhat type) {
    super(name, SignatureStorage.DYNAMIC, type);
  }

  protected abstract void newInstance(GeneratorAdapter method);

  @Override
  public final void build(
      GeneratorAdapter method, Type initialType, Stream<SignableRenderer> variables) {
    newInstance(method);
    variables.forEach(
        signableRenderer ->
            signableRenderer.render(
                method,
                (m, target) -> {
                  m.dup();
                  m.push(target.name());
                  Renderer.loadImyhatInMethod(m, target.type().descriptor());
                  m.loadArg(0);
                  if (target instanceof InputVariable) {
                    ((InputVariable) target).extract(m);
                  } else {
                    m.invokeVirtual(
                        initialType,
                        new Method(
                            target.name(), target.type().apply(TypeUtils.TO_ASM), new Type[] {}));
                  }
                  m.valueOf(target.type().apply(TypeUtils.TO_ASM));
                  m.invokeInterface(A_DYNAMIC_SIGNER_TYPE, METHOD_DYNAMIC_SIGNER__ADD_VARIABLE);
                }));
    method.invokeInterface(A_DYNAMIC_SIGNER_TYPE, METHOD_DYNAMIC_SIGNER__FINISH);
    method.unbox(type().apply(TypeUtils.TO_ASM));
  }
}
