package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Supplier;

/** A value that can be put on the operand stack in a method. */
public abstract class EcmaLoadableValue implements Supplier<String> {
  /** The variable name of this value */
  public abstract String name();
}
