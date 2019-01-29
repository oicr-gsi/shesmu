package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

/** Define a <tt>Input</tt> format for olives to consume */
public interface InputFormatDefinition {
  /** Get all the variables available for this format */
  Stream<? extends Target> baseStreamVariables();

  /** Load the class for this format onto the stack */
  Type type();

  /** The name of this format, which must be a valid Shesmu identifier */
  String name();
}
