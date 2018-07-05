package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Pair;

/**
 * Creates bytecode for a “Define”-style olive to be used in “Matches” clauses
 */
public final class OliveDefineBuilder extends BaseOliveBuilder {

	private static class LoadParameter extends LoadableValue {
		private final int index;
		private final String name;
		private final Type type;

		public LoadParameter(int index, Target source) {
			super();
			this.index = index;
			name = source.name();
			type = source.type().asmType();
		}

		@Override
		public void accept(Renderer t) {
			t.methodGen().loadArg(index + 1);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Type type() {
			return type;
		}

	}

	private final Method method;

	private final List<LoadableValue> parameters;

	public OliveDefineBuilder(RootBuilder owner, int oliveId, Stream<? extends Target> parameters) {
		super(owner, oliveId, owner.inputFormatDefinition().type());
		this.parameters = parameters.map(Pair.number()).map(Pair.transform(LoadParameter::new))
				.collect(Collectors.toList());
		method = new Method(String.format("olive_matcher_%d", oliveId), A_STREAM_TYPE,
				Stream.concat(Stream.of(A_STREAM_TYPE), this.parameters.stream().map(LoadableValue::type))
						.toArray(Type[]::new));
	}

	/**
	 * Writes the byte code for this method.
	 *
	 * This must be called before using this in a “Matches” clause.
	 */
	public void finish() {
		final Renderer renderer = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor), 0, null,
				parameters.stream());
		renderer.methodGen().visitCode();
		renderer.methodGen().loadArg(0);
		steps.forEach(step -> step.accept(renderer));
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();

	}

	@Override
	public Stream<LoadableValue> loadableValues() {
		return Stream.concat(parameters.stream(), owner.constants());
	}

	/**
	 * The method definition for this matcher
	 */
	public Method method() {
		return method;
	}

	/**
	 * The number of bound parameters
	 */
	public int parameters() {
		return parameters.size();
	}

	/**
	 * The type of a bound parameter
	 */
	public Type parameterType(int i) {
		return parameters.get(i).type();
	}

}
