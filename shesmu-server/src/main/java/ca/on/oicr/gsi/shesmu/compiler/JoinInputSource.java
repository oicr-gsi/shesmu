package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import org.objectweb.asm.Type;

public interface JoinInputSource {

  InputFormatDefinition format();

  String name();

  void render(Renderer renderer);

  Type type();
}
