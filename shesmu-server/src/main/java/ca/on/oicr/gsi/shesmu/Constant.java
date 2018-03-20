package ca.on.oicr.gsi.shesmu;

import java.time.Instant;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.compiler.LoadableValue;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;

public abstract class Constant extends Target {

	private static Method INSTANT_CTOR = new Method("ofEpochMilli", Imyhat.DATE.asmType(),
			new Type[] { Type.LONG_TYPE });

	public static Constant of(String name, boolean value) {
		return new Constant(name, Imyhat.BOOLEAN) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

	public static Constant of(String name, Instant value) {
		return new Constant(name, Imyhat.DATE) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value.toEpochMilli());
				methodGen.invokeStatic(type().asmType(), INSTANT_CTOR);
			}
		};
	}

	public static Constant of(String name, long value) {
		return new Constant(name, Imyhat.INTEGER) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				methodGen.push(value);
			}
		};
	}

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

	public Constant(String name, Imyhat type) {
		super();
		this.name = name;
		this.type = type;
	}

	public final LoadableValue asLoadable() {
		return loadable;
	}

	@Override
	public final Flavour flavour() {
		return Flavour.CONSTANT;
	}

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
