package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.plugin.signature.StaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Create a signature variable for a subclass of {@link StaticSigner} */
public abstract class SignatureVariableForStaticSigner extends SignatureDefinition {
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SIGNER_TYPE = Type.getType(StaticSigner.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Method METHOD_STATIC_SIGNER__ADD_VARIABLE =
      new Method("addVariable", Type.VOID_TYPE, new Type[] {A_STRING_TYPE, A_IMYHAT_TYPE});

  private static final Method METHOD_STATIC_SIGNER__FINISH =
      new Method("finish", A_OBJECT_TYPE, new Type[] {});

  public SignatureVariableForStaticSigner(String name, Imyhat type) {
    super(name, SignatureStorage.STATIC, type);
  }

  @Override
  public final void build(GeneratorAdapter method, Type initialType, Stream<Target> variables) {
    newInstance(method);
    variables.forEach(
        target -> {
          method.dup();
          method.push(target.name());
          Renderer.loadImyhatInMethod(method, target.type().descriptor());
          method.invokeInterface(A_SIGNER_TYPE, METHOD_STATIC_SIGNER__ADD_VARIABLE);
        });
    method.invokeInterface(A_SIGNER_TYPE, METHOD_STATIC_SIGNER__FINISH);
    method.unbox(type().apply(TypeUtils.TO_ASM));
  }

  protected abstract void newInstance(GeneratorAdapter method);
}
