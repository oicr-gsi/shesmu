package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ExpressionNodeLogicalOr extends ExpressionNodeBinarySymmetric {
	public ExpressionNodeLogicalOr(int line, int column, ExpressionNode left, ExpressionNode right) {
		super(line, column, Imyhat.BOOLEAN, left, right);
	}

	@Override
	public void render(Renderer renderer) {
		left().render(renderer);
		final Label end = renderer.methodGen().newLabel();
		renderer.methodGen().dup();
		renderer.methodGen().ifZCmp(GeneratorAdapter.NE, end);
		right().render(renderer);
		renderer.methodGen().mark(end);
	}

}
