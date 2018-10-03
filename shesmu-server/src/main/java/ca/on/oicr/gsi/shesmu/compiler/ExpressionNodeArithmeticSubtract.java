package ca.on.oicr.gsi.shesmu.compiler;

import java.time.Instant;
import java.util.function.Consumer;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;

public class ExpressionNodeArithmeticSubtract extends ExpressionNodeBinary {
	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	private static final Method METHOD_INSTANT__MINUS_SECONDS = new Method("minusSeconds", A_INSTANT_TYPE,
			new Type[] { Type.LONG_TYPE });
	private static final Method METHOD_RUNTIME_SUPPORT__DIFFERENCE = new Method("difference", Type.LONG_TYPE,
			new Type[] { A_INSTANT_TYPE, A_INSTANT_TYPE });

	public ExpressionNodeArithmeticSubtract(int line, int column, ExpressionNode left, ExpressionNode right) {
		super(line, column, left, right);
	}

	@Override
	public void render(Renderer renderer) {
		left().render(renderer);
		right().render(renderer);
		renderer.mark(line());

		if (left().type().isSame(Imyhat.DATE)) {
			if (right().type().isSame(Imyhat.INTEGER)) {
				renderer.methodGen().invokeVirtual(A_INSTANT_TYPE, METHOD_INSTANT__MINUS_SECONDS);
				return;
			}
			if (right().type().isSame(Imyhat.DATE)) {
				renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__DIFFERENCE);
				return;
			}
		}
		if (left().type().isSame(Imyhat.INTEGER)) {
			if (right().type().isSame(Imyhat.INTEGER)) {
				renderer.methodGen().math(GeneratorAdapter.SUB, Type.LONG_TYPE);
				return;
			}
		}
		throw new UnsupportedOperationException();
	}

	@Override
	protected Imyhat typeCheck(Imyhat left, Imyhat right, Consumer<String> errorHandler) {
		if (left.isSame(Imyhat.DATE)) {
			if (right.isSame(Imyhat.INTEGER)) {
				return Imyhat.DATE;
			}
			if (right.isSame(Imyhat.DATE)) {
				return Imyhat.INTEGER;
			}
			typeError("integer or date on right", right, errorHandler);
		}
		if (left.isSame(Imyhat.INTEGER)) {
			if (right.isSame(Imyhat.INTEGER)) {
				return Imyhat.INTEGER;
			}
			typeError("integer", right, errorHandler);
		}
		typeError("integer or date on left", left, errorHandler);
		return Imyhat.BAD;
	}

}
