package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.SignatureVariable;

/**
 * Creates bytecode for a “Define”-style olive to be used in “Call” clauses
 */
public final class OliveDefineBuilder extends BaseOliveBuilder {

	private final Method method;
	private final List<LoadableValue> parameters;

	private final String signerPrefix;

	public OliveDefineBuilder(RootBuilder owner, String name, Stream<? extends Target> parameters) {
		super(owner, owner.inputFormatDefinition().type());
		this.parameters = parameters.map(Pair.number(4 + (int) NameDefinitions.signatureVariables().count()))
				.map(Pair.transform(LoadParameter::new)).collect(Collectors.toList());
		method = new Method(String.format("Define %s", name), A_STREAM_TYPE, Stream.concat(//
				Stream.concat(//
						Stream.of(A_STREAM_TYPE, A_INPUT_PROVIDER_TYPE, Type.INT_TYPE, Type.INT_TYPE), //
						NameDefinitions.signatureVariables().map(SignatureVariable::storageType)), //
				this.parameters.stream().map(LoadableValue::type)).toArray(Type[]::new));
		signerPrefix = String.format("Define %s ", name);
		NameDefinitions.signatureVariables().forEach(signer -> {
			owner.classVisitor.visitField(Opcodes.ACC_PRIVATE, signerPrefix + signer.name(),
					signer.storageType().getDescriptor(), null, null).visitEnd();
		});
	}

	@Override
	protected void emitSigner(SignatureVariable signer, Renderer renderer) {
		final String name = signerPrefix + signer.name();
		switch (signer.storage()) {
		case STATIC_METHOD:
			renderer.methodGen().loadThis();
			renderer.methodGen().getField(owner.selfType(), name, A_FUNCTION_TYPE);
			renderer.loadStream();
			renderer.methodGen().invokeInterface(A_FUNCTION_TYPE, METHOD_FUNCTION__APPLY);
			renderer.methodGen().unbox(signer.type().asmType());
			break;
		case STATIC_FIELD:
			renderer.methodGen().loadThis();
			renderer.methodGen().getField(owner.selfType(), name, signer.type().asmType());
			break;
		default:
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Writes the byte code for this method.
	 *
	 * This must be called before using this in a “Call” clause.
	 */
	public void finish() {
		final Renderer renderer = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor), 0, null,
				parameters.stream(), this::emitSigner);
		renderer.methodGen().visitCode();
		NameDefinitions.signatureVariables().map(Pair.number()).forEach(pair -> {
			renderer.methodGen().loadThis();
			renderer.methodGen().loadArg(pair.first() + 4);
			renderer.methodGen().putField(owner.selfType(), signerPrefix + pair.second().name(),
					pair.second().storageType());
		});

		renderer.methodGen().loadArg(0);
		steps.forEach(step -> step.accept(renderer));
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();

	}

	@Override
	public Stream<LoadableValue> loadableValues() {
		return Stream.concat(parameters.stream(), owner.constants(true));
	}

	@Override
	protected void loadOwnerSourceLocation(GeneratorAdapter method) {
		method.loadArg(2);
		method.loadArg(3);
	}

	@Override
	protected void loadSigner(SignatureVariable signer, Renderer renderer) {
		final String name = signerPrefix + signer.name();
		renderer.methodGen().loadThis();
		renderer.methodGen().getField(owner.selfType(), name, signer.storageType());
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
