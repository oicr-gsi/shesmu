package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import org.objectweb.asm.commons.GeneratorAdapter;

public interface InputVariable extends Target {

  /** Given the stream value on the stack, extract this input value */
  void extract(GeneratorAdapter method);
}
