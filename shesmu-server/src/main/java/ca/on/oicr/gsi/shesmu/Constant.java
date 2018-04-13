package ca.on.oicr.gsi.shesmu;

import java.time.Instant;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.compiler.LoadableValue;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;

/**
 * A constant value that can be injected into a Shesmu program
 *
 * Constant values get written into the program, so they are not updated until
 * the program is recompiled even if the {@link ConstantSource}
 */
public abstract class Constant extends Target {

	private static Method INSTANT_CTOR = new Method("ofEpochMilli", Imyhat.DATE.asmType(),
			new Type[] { Type.LONG_TYPE });

	/**
	 * Define a boolean constant
	 *
	 * @param name
	 *            the name, which must be a valid Shesmu identifier
	 */
	public static Constant of(String name, boolean value) {
		return new Constant(name, Imyhat.BOOLEAN) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

	/**
	 * Define a date constant
	 *
	 * @param name
	 *            the name, which must be a valid Shesmu identifier
	 */
	public static Constant of(String name, Instant value) {
		return new Constant(name, Imyhat.DATE) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value.toEpochMilli());
				methodGen.invokeStatic(type().asmType(), INSTANT_CTOR);
			}
		};
	}

	/**
	 * Define an integer constant
	 *
	 * @param name
	 *            the name, which must be a valid Shesmu identifier
	 */
	public static Constant of(String name, long value) {
		return new Constant(name, Imyhat.INTEGER) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

	/**
	 * Define a string constant
	 *
	 * @param name
	 *            the name, which must be a valid Shesmu identifier
	 */
	public static Constant of(String name, String value) {
		return new Constant(name, Imyhat.STRING) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

	private final LoadableValue loadable = new LoadableValue() {

		@Override
		public void accept(Renderer renderer) {
			load(renderer.methodGen());
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Type type() {
			return type.asmType();
		}
	};

	private final String name;

	private final Imyhat type;

	/**
	 * Create a new constant
	 *
	 * @param name
	 *            the name of the constant, which must be valid Shesmu identifier
	 * @param type
	 *            the Shemsu type of the constant
	 */
	public Constant(String name, Imyhat type) {
		super();
		this.name = name;
		this.type = type;
	}

	/**
	 * Convert the constant into a form that can be used during bytecode generation
	 */
	public final LoadableValue asLoadable() {
		return loadable;
	}

	@Override
	public final Flavour flavour() {
		return Flavour.CONSTANT;
	}

	/**
	 * Generate bytecode in the supplied method to load this constant on the operand
	 * stack.
	 *
	 * @param methodGen
	 *            the method to load the value in
	 */
	protected abstract void load(GeneratorAdapter methodGen);

	@Override
	public final String name() {
		return name;
	}

	@Override
	public final Imyhat type() {
		return type;
	}

}
