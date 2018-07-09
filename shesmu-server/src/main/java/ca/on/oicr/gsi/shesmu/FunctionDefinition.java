package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A multi-keyed map that functions a value based on rules/tables
 */
public interface FunctionDefinition {

	/**
	 * Define a function that binds to a static method
	 *
	 * @param owner
	 *            the class containing the static method
	 * @param methodName
	 *            the name of the static method
	 * @param description
	 *            the help text for the method
	 * @param returnType
	 *            the return type of the method (the appropriate Java type will be
	 *            matched)
	 * @param argumentTypes
	 *            the types of the arguments to the method (the appropriate Java
	 *            types will be matched)
	 */
	@SafeVarargs
	public static FunctionDefinition staticMethod(Class<?> owner, String methodName, String description,
			Imyhat returnType, FunctionParameter... parameters) {
		return new FunctionDefinition() {

			@Override
			public String description() {
				return description;
			}

			@Override
			public String name() {
				return methodName;
			}

			@Override
			public Stream<FunctionParameter> parameters() {
				return Stream.of(parameters);
			}

			@Override
			public void render(GeneratorAdapter methodGen) {
				methodGen.invokeStatic(Type.getType(owner), new Method(methodName, returnType.asmType(),
						Stream.of(parameters).map(p -> p.type().asmType()).toArray(Type[]::new)));
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}
		};
	}

	/**
	 * Documentation about how this function works
	 */
	String description();

	/**
	 * The name of the function.
	 */
	String name();

	/**
	 * The parameters of the function, in order
	 */
	Stream<FunctionParameter> parameters();

	/**
	 * Create bytecode for this function.
	 */
	void render(GeneratorAdapter methodGen);

	/**
	 * The return type of the map
	 */
	Imyhat returnType();
}
