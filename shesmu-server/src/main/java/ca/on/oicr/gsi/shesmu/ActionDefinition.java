package ca.on.oicr.gsi.shesmu;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Describes an action that can be invoked by the Shesmu language. */
public abstract class ActionDefinition {

  private final String description;

  private final String name;

  private final List<ActionParameterDefinition> parameters;

  private final Type type;

  public ActionDefinition(
      String name, Type type, String description, Stream<ActionParameterDefinition> parameters) {
    this.name = name;
    this.type = type;
    this.description = description;
    this.parameters = parameters.collect(Collectors.toList());
  }

  /** A human-readable explanation of what this action does and where it came from. */
  public final String description() {
    return description;
  }

  /**
   * Write the bytecode to create a new instance of the action.
   *
   * <p>This method should create an new instance of the action and leave it on the stack. If there
   * is any hidden state (remote server addresses, workflow identifier), they must be handled at
   * this stage.
   *
   * @param methodGen the method to generate the bytecode in
   */
  public abstract void initialize(GeneratorAdapter methodGen);

  /**
   * The name of the action as it will appear in the Shesmu language
   *
   * <p>It must be a valid Shesmu identifier.
   */
  public final String name() {
    return name;
  }

  /** List all the parameters that must be set for this action to be performed. */
  public final Stream<ActionParameterDefinition> parameters() {
    return parameters.stream();
  }

  /**
   * The Java type for the action.
   *
   * <p>The action maybe put in local variables or function parameters as part of handling, so it
   * must know the type of the action. This type must be a subclass of {@link Action}, but this is
   * not checked (it will just explode spectacularly at runtime).
   */
  public final Type type() {
    return type;
  }
}
