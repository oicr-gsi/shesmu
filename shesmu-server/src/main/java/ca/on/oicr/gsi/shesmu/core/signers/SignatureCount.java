package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.compiler.Target;
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
  public void build(GeneratorAdapter method, Type initialType, Stream<Target> variables) {
    method.push(variables.count());
  }

  @Override
  public Path filename() {
    return null;
  }
}
