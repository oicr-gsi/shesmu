package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Describes an action that can be invoked by the Shesmu language. */
public abstract class ActionDefinition {

  private final String description;
  private final Path filename;

  private final String name;

  private final List<ActionParameterDefinition> parameters;

  /**
   * Constructs a new action definition
   *
   * @param name the name of the action which must be a valid Shesmu identifier
   * @param description a human-readable description of the action
   * @param filename the file where the action is defined, or null if not appropriate
   * @param parameters the parameters that can be set on this action
   */
  public ActionDefinition(
      String name,
      String description,
      Path filename,
      Stream<ActionParameterDefinition> parameters) {
    this.name = name;
    this.description = description;
    this.filename = filename;
    this.parameters = parameters.collect(Collectors.toList());
  }

  /**
   * A human-readable explanation of what this action does and where it came from.
   *
   * @return the description
   */
  public final String description() {
    return description;
  }

  /**
   * The configuration file associated with this parameter, if one exists.
   *
   * @return the filepath
   */
  public Path filename() {
    return filename;
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
   *
   * @return the name
   */
  public final String name() {
    return name;
  }

  /**
   * List all the parameters that can be set for this action to be performed.
   *
   * @return the parameters for the action
   */
  public final Stream<ActionParameterDefinition> parameters() {
    return parameters.stream();
  }

  /**
   * Additional information that can be displayed in the user interface with formatting
   *
   * @return a generator for the additional information
   */
  public SupplementaryInformation supplementaryInformation() {
    return Stream::empty;
  }
}
