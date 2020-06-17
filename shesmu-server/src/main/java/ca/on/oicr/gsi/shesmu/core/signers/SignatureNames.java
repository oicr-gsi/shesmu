package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureStorage;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class SignatureNames extends SignatureDefinition {
  private static final Type A_TREE_SET_TYPE = Type.getType(TreeSet.class);

  private static final Method CTOR_DEFAULT = new Method("<init>", Type.VOID_TYPE, new Type[] {});

  private static final Method METHOD_TREE_SET__ADD =
      new Method("add", Type.BOOLEAN_TYPE, new Type[] {Type.getType(Object.class)});

  public SignatureNames() {
    super(
        String.join(Parser.NAMESPACE_SEPARATOR, "std", "signature", "names"),
        SignatureStorage.STATIC,
        Imyhat.STRING.asList());
  }

  @Override
  public void build(GeneratorAdapter method, Type initialType, Stream<Target> variables) {
    method.newInstance(A_TREE_SET_TYPE);
    method.dup();
    method.invokeConstructor(A_TREE_SET_TYPE, CTOR_DEFAULT);

    variables.forEach(
        target -> {
          method.dup();
          method.push(target.name());
          method.invokeVirtual(A_TREE_SET_TYPE, METHOD_TREE_SET__ADD);
          method.pop();
        });
  }

  @Override
  public Path filename() {
    return null;
  }
}
