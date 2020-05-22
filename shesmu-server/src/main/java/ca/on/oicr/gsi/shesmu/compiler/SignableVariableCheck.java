package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;

public interface SignableVariableCheck {

  String name();

  void render(GeneratorAdapter methodGen);
}
