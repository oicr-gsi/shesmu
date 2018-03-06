package ca.on.oicr.gsi.shesmu;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Describes an action that can be invoked by the Shesmu language.
 */
public abstract class ActionDefinition {

	private final String name;

	private final List<ParameterDefinition> parameters;

	private final Type type;

	public ActionDefinition(String name, Type type, Stream<ParameterDefinition> parameters) {
		this.name = name;
		this.type = type;
		this.parameters = parameters.collect(Collectors.toList());
	}

	/**
	 * Write the bytecode to create a new instance of the action.
	 *
	 * This method should create an new instance of the action and leave it on the
	 * stack.
	 *
	 * @param methodGen
	 *            the method to generate the bytecode in
	 */
	public abstract void initialize(GeneratorAdapter methodGen);

	/**
	 * The name of the action as it will appear in the Shesmu language
	 *
	 * It must be a valid Shesmu identifier.
	 */
	public final String name() {
		return name;
	}

	/**
	 * List all the parameters that must be set for this action to be performed.
	 */
	public final Stream<ParameterDefinition> parameters() {
		return parameters.stream();
	}

	public final Type type() {
		return type;
	}
}
