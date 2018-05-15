package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A multi-keyed map that functions a value based on rules/tables
 */
public interface FunctionDefinition {

	public static FunctionDefinition staticMethod(Class<?> owner, String methodName, String description,
			Imyhat returnType, Imyhat... argumentTypes) {
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
			public void render(GeneratorAdapter methodGen) {
				methodGen.invokeStatic(Type.getType(owner), new Method(methodName, returnType.asmType(),
						Stream.of(argumentTypes).map(Imyhat::asmType).toArray(Type[]::new)));
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}

			@Override
			public Stream<Imyhat> types() {
				return Stream.of(argumentTypes);
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
	 * Create bytecode for this function.
	 */
	void render(GeneratorAdapter methodGen);

	/**
	 * The return type of the map
	 */
	Imyhat returnType();

	/**
	 * The types of the parameters
	 */
	Stream<Imyhat> types();
}
