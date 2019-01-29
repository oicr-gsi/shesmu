package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

/** A definition for a parameter that should be user-definable for an action */
public interface ActionParameterDefinition {

  /** The name of the parameter as the user will set it. */
  String name();

  /**
   * Whether this parameter is required or not.
   *
   * <p>If not required, the user may omit setting the value.s
   */
  boolean required();

  /**
   * A procedure to write the bytecode to set the parameter in the action instance
   *
   * @param renderer The method where the code is being generated
   * @param actionLocal The local variable holding the action being populated
   * @param loadParameter a callback to load the desired value for the parameter; it should be
   *     called exactly once.
   */
  void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter);

  /** The type of the parameter */
  Imyhat type();
}
