package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public interface CallableDefinitionRenderer {

  Type currentType();

  void generateAppendInputFormats(GeneratorAdapter methodGen);

  void generateCall(GeneratorAdapter methodGen);

  void generatePreamble(GeneratorAdapter methodGen);

  String name();

  Type parameter(int i);

  int parameters();
}
