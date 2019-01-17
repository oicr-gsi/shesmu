package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;
import org.objectweb.asm.Type;

/** A value that can be put on the operand stack in a method. */
public abstract class LoadableValue implements Consumer<Renderer> {
  /** The variable name of this value */
  public abstract String name();

  /** The Java/ASM type for this value */
  public abstract Type type();
}
