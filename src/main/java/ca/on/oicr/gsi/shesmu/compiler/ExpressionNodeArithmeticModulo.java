package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ExpressionNodeArithmeticModulo extends ExpressionNodeBinarySymmetric {

	public ExpressionNodeArithmeticModulo(int line, int column, ExpressionNode left, ExpressionNode right) {
		super(line, column, Imyhat.INTEGER, left, right);
	}

	@Override
	public void render(Renderer renderer) {
		left().render(renderer);
		right().render(renderer);
		renderer.mark(line());

		renderer.methodGen().math(GeneratorAdapter.REM, Type.LONG_TYPE);
	}

}
