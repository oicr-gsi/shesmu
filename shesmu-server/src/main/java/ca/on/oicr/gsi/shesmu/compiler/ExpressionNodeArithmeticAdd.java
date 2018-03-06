package ca.on.oicr.gsi.shesmu.compiler;

import java.time.Instant;
import java.util.function.Consumer;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ExpressionNodeArithmeticAdd extends ExpressionNodeBinary {
	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final Method METHOD_INSTANT__PLUS_SECONDS = new Method("plusSeconds", A_INSTANT_TYPE,
			new Type[] { Type.LONG_TYPE });

	public ExpressionNodeArithmeticAdd(int line, int column, ExpressionNode left, ExpressionNode right) {
		super(line, column, left, right);
	}

	@Override
	public void render(Renderer renderer) {
		left().render(renderer);
		right().render(renderer);
		renderer.mark(line());
		if (left().type().isSame(Imyhat.DATE)) {
			if (right().type().isSame(Imyhat.INTEGER)) {
				renderer.methodGen().invokeVirtual(A_INSTANT_TYPE, METHOD_INSTANT__PLUS_SECONDS);
				return;
			}
		}
		if (left().type().isSame(Imyhat.INTEGER)) {
			if (right().type().isSame(Imyhat.INTEGER)) {
				renderer.methodGen().math(GeneratorAdapter.ADD, Type.LONG_TYPE);
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
			typeError("integer on right", right, errorHandler);
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
