package ca.on.oicr.gsi.shesmu;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.compiler.LoadableValue;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;

/**
 * A constant value that can be injected into a Shesmu program
 *
 * Constant values get written into the program, so they are not updated until
 * the program is recompiled even if the {@link ConstantSource}
 *
 * They aren't constant in the sense that they can be arbitrary bytecode, so
 * <tt>now</tt> is considered a constant even though it varies. All that matters
 * is that it has no direct interaction with any other part of the Shesmu
 * script.
 */
public abstract class Constant extends Target {

	private class ConstantCompiler extends BaseHotloadingCompiler {

		public ConstantLoader compile() {
			final ClassVisitor classVisitor = createClassVisitor();
			classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "dyn/shesmu/Constant", null,
					A_OBJECT_TYPE.getInternalName(), new String[] { A_CONSTANT_LOADER_TYPE.getInternalName() });

			final GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, DEFAULT_CTOR, null, null,
					classVisitor);
			ctor.visitCode();
			ctor.loadThis();
			ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
			ctor.visitInsn(Opcodes.RETURN);
			ctor.visitMaxs(0, 0);
			ctor.visitEnd();

			final GeneratorAdapter handle = new GeneratorAdapter(Opcodes.ACC_PUBLIC, LOAD_METHOD, null, null,
					classVisitor);
			handle.visitCode();
			handle.invokeDynamic(type.signature(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
			handle.loadArg(0);
			handle.push("value");
			Constant.this.load(handle);
			handle.box(type.asmType());
			handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__PACK_JSON);
			handle.visitInsn(Opcodes.RETURN);
			handle.visitMaxs(0, 0);
			handle.visitEnd();

			classVisitor.visitEnd();

			try {
				return load(ConstantLoader.class, "dyn.shesmu.Constant");
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				e.printStackTrace();
				return o -> o.put("error", e.getMessage());
			}
		}
	}

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

	/**
	 * Write the value of a constant into the <tt>value</tt> property of a JSON
	 * object.
	 */
	public interface ConstantLoader {
		@RuntimeInterop
		public void load(ObjectNode target);
	}

	private static final Type A_CONSTANT_LOADER_TYPE = Type.getType(ConstantLoader.class);

	private static final Type A_HASH_SET_TYPE = Type.getType(HashSet.class);

	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);

	private static final Type A_JSON_OBJECT_TYPE = Type.getType(ObjectNode.class);

	private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private static final Handle HANDLER_IMYHAT = new Handle(Opcodes.H_INVOKESTATIC, A_IMYHAT_TYPE.getInternalName(),
			"bootstrap", Type.getMethodDescriptor(Type.getType(CallSite.class),
					Type.getType(MethodHandles.Lookup.class), A_STRING_TYPE, Type.getType(MethodType.class)),
			false);

	private static Method INSTANT_CTOR = new Method("ofEpochMilli", Imyhat.DATE.asmType(),
			new Type[] { Type.LONG_TYPE });

	private static final Method LOAD_METHOD = new Method("load", Type.VOID_TYPE, new Type[] { A_JSON_OBJECT_TYPE });

	private static final Method METHOD_IMYHAT__PACK_JSON = new Method("packJson", Type.VOID_TYPE,
			new Type[] { A_OBJECT_NODE_TYPE, A_STRING_TYPE, A_OBJECT_TYPE });

	private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);

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

	/**
	 * Generate a class that write the constant to JSON when called.
	 */
	public final ConstantLoader compile() {
		return new ConstantCompiler().compile();
	}

	/**
	 * The documentation text for a constant.
	 */
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

	/**
	 * The name of the constant.
	 *
	 * This must be a valid identifer.
	 */
	@Override
	public final String name() {
		return name;
	}

	/**
	 * The type of the constant.
	 *
	 * Although a constant can have any type, there isn't a straight-forward
	 * implementation for arbitrary types, so only simple types are provided here.
	 */
	@Override
	public final Imyhat type() {
		return type;
	}

}
