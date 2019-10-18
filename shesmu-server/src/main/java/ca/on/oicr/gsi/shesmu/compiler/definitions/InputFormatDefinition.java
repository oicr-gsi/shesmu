package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

/** Define a <tt>Input</tt> format for olives to consume */
public interface InputFormatDefinition {
  InputFormatDefinition DUMMY =
      new InputFormatDefinition() {
        @Override
        public Stream<? extends Target> baseStreamVariables() {
          return Stream.empty();
        }

        @Override
        public Type type() {
          return Type.getType(Void.class);
        }

        @Override
        public String name() {
          return "";
        }
      };

  /** Get all the variables available for this format */
  Stream<? extends Target> baseStreamVariables();

  /** Load the class for this format onto the stack */
  Type type();

  /** The name of this format, which must be a valid Shesmu identifier */
  String name();
}
