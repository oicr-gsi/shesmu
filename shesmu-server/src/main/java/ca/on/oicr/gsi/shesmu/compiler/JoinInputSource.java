package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.util.Optional;
import org.objectweb.asm.Type;

public interface JoinInputSource {
  Optional<CallableDefinitionRenderer> additionalFormatCollector();

  InputFormatDefinition format();

  String name();

  void render(Renderer renderer);

  Type type();
}
