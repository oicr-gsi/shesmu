package ca.on.oicr.gsi.shesmu;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	public static abstract class ConstantList<T> extends Constant {

		private final List<T> values;

		public ConstantList(String name, Imyhat type, Stream<T> values, String description) {
			super(name, type.asList(), description);
			this.values = values.collect(Collectors.toList());
		}

		@Override
		protected final void load(GeneratorAdapter methodGen) {
			methodGen.newInstance(A_HASH_SET_TYPE);
			methodGen.dup();
			methodGen.invokeConstructor(A_HASH_SET_TYPE, DEFAULT_CTOR);
			for (final T value : values) {
				methodGen.dup();
				write(methodGen, value);
				methodGen.invokeVirtual(A_HASH_SET_TYPE, SET__ADD);
				methodGen.pop();
			}
		}

		protected abstract void write(GeneratorAdapter methodGen, T value);

	}

	private static final Type A_HASH_SET_TYPE = Type.getType(HashSet.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private static Method INSTANT_CTOR = new Method("ofEpochMilli", Imyhat.DATE.asmType(),
			new Type[] { Type.LONG_TYPE });

	private static final Method SET__ADD = new Method("add", Type.BOOLEAN_TYPE,
			new Type[] { Type.getType(Object.class) });

	/**
	 * Define a boolean constant
	 *
	 * @param name
	 *            the name, which must be a valid Shesmu identifier
	 */
	public static Constant of(String name, boolean value, String description) {
		return new Constant(name, Imyhat.BOOLEAN, description) {

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
	public static Constant of(String name, Instant value, String description) {
		return new Constant(name, Imyhat.DATE, description) {

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
	public static Constant of(String name, long value, String description) {
		return new Constant(name, Imyhat.INTEGER, description) {

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
	public static Constant of(String name, String value, String description) {
		return new Constant(name, Imyhat.STRING, description) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

	public static Constant ofBooleans(String name, Stream<Boolean> stream, String description) {
		return new ConstantList<Boolean>(name, Imyhat.BOOLEAN, stream, description) {

			@Override
			protected void write(GeneratorAdapter methodGen, Boolean value) {
				methodGen.push(value);
				methodGen.box(Type.BOOLEAN_TYPE);
			}
		};
	}

	public static Constant ofLongs(String name, Stream<Long> stream, String description) {
		return new ConstantList<Long>(name, Imyhat.INTEGER, stream, description) {

			@Override
			protected void write(GeneratorAdapter methodGen, Long value) {
				methodGen.push(value);
				methodGen.box(Type.LONG_TYPE);
			}
		};
	}

	public static Constant ofStrings(String name, Stream<String> stream, String description) {
		return new ConstantList<String>(name, Imyhat.STRING, stream, description) {

			@Override
			protected void write(GeneratorAdapter methodGen, String value) {
				methodGen.push(value);
			}
		};
	}

	private final String description;

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
	public Constant(String name, Imyhat type, String description) {
		super();
		this.name = name;
		this.type = type;
		this.description = description;
	}

	/**
	 * Convert the constant into a form that can be used during bytecode generation
	 */
	public final LoadableValue asLoadable() {
		return loadable;
	}

	public final String description() {
		return description;
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
