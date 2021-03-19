package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.compiler.SignableRenderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureStorage;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public final class SignatureCount extends SignatureDefinition {

  public SignatureCount() {
    super(
        String.join(Parser.NAMESPACE_SEPARATOR, "std", "signature", "count"),
        SignatureStorage.STATIC,
        Imyhat.INTEGER);
  }

  @Override
  public void build(GeneratorAdapter method, Type initialType, Stream<SignableRenderer> variables) {
    final var count = method.newLocal(Type.INT_TYPE);
    method.push(0);
    method.storeLocal(count);
    variables.forEach(
        signableRenderer ->
            signableRenderer.render(
                method,
                (m, t) -> {
                  m.loadLocal(count);
                  m.push(1);
                  m.math(GeneratorAdapter.ADD, Type.INT_TYPE);
                  m.storeLocal(count);
                }));
    method.loadLocal(count);
    method.cast(Type.INT_TYPE, Type.LONG_TYPE);
  }

  @Override
  public Path filename() {
    return null;
  }
}
