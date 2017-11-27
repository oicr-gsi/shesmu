package ca.on.oicr.gsi.shesmu;

import java.util.function.Consumer;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;

/**
 * A definition for a parameter that should be user-definable for an action
 */
public interface ParameterDefinition {
	/**
	 * Create a string parameter definition that will be written to a public field
	 *
	 * @param owner
	 *            The type of the action object
	 * @param fieldName
	 *            the name of the field
	 */
	public static ParameterDefinition forStringField(Type owner, String fieldName) {
		return new ParameterDefinition() {

			@Override
			public String name() {
				return fieldName;
			}

			@Override
			public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
				renderer.methodGen().loadLocal(actionLocal);
				loadParameter.accept(renderer);
				renderer.methodGen().putField(owner, fieldName, Type.getType(String.class));
			}

			@Override
			public Imyhat type() {
				return Imyhat.STRING;
			}

		};
	}

	/**
	 * The name of the parameter as the user will set it.
	 */
	String name();

	/**
	 * A procedure to write the bytecode to set the parameter in the action instance
	 *
	 * @param renderer
	 *            The method where the code is being generated
	 * @param actionLocal
	 *            The local variable holding the action being populated
	 * @param loadParameter
	 *            a callback to load the desired value for the parameter; it should
	 *            be called exactly once.
	 */
	void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter);

	/**
	 * The type of the parameter
	 */
	Imyhat type();

}
