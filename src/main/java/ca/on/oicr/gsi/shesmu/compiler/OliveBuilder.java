package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * An olive that will result in an action being performed
 */
public final class OliveBuilder extends BaseOliveBuilder {
	private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);

	private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);

	private static final Method METHOD_CONSUMER__ACCEPT = new Method("accept", VOID_TYPE, new Type[] { A_OBJECT_TYPE });

	private static final Method METHOD_SUPPLIER__GET = new Method("get", A_OBJECT_TYPE, new Type[] {});

	@SafeVarargs
	public OliveBuilder(RootBuilder owner, int oliveId, Type initialType, LoadableValue... captures) {
		super(owner, oliveId, initialType);
	}

	/**
	 * Run an action
	 *
	 * Consume an action from the stack and queue to be executed by the server
	 *
	 * @param methodGen
	 *            the method generator, which must be the method generator produced
	 *            by {@link #finish()}
	 */
	public void emitAction(GeneratorAdapter methodGen, int local) {
		methodGen.loadArg(0);
		methodGen.loadLocal(local);
		methodGen.invokeInterface(A_CONSUMER_TYPE, METHOD_CONSUMER__ACCEPT);
	}

	/**
	 * Generate bytecode for the olive and create a method to consume the result.
	 */
	public final Renderer finish() {
		final GeneratorAdapter runMethod = owner.rootRenderer().methodGen();
		runMethod.loadArg(1);
		runMethod.invokeInterface(A_SUPPLIER_TYPE, METHOD_SUPPLIER__GET);
		runMethod.checkCast(A_STREAM_TYPE);

		steps.forEach(step -> step.accept(owner.rootRenderer()));

		runMethod.loadThis();
		runMethod.loadArg(0);
		final Method method = new Method(String.format("olive_%d_consume", oliveId), VOID_TYPE,
				new Type[] { A_CONSUMER_TYPE, currentType() });
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		runMethod.invokeDynamic("accept", Type.getMethodDescriptor(A_CONSUMER_TYPE, owner.selfType(), A_CONSUMER_TYPE),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(VOID_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(VOID_TYPE, currentType()));

		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor), 1,
				currentType(), Collections.emptyMap());
	}

	@Override
	public Stream<LoadableValue> loadableValues() {
		return Stream.empty();
	}

}
