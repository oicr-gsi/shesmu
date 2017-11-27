package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ExpressionNodeBinarySymmetric extends ExpressionNodeBinary {

	private final Imyhat requiredType;

	public ExpressionNodeBinarySymmetric(int line, int column, Imyhat requiredType, ExpressionNode left,
			ExpressionNode right) {
		super(line, column, left, right);
		this.requiredType = requiredType;
	}

	@Override
	protected final Imyhat typeCheck(Imyhat left, Imyhat right, Consumer<String> errorHandler) {
		boolean ok = true;
		if (!left.isSame(requiredType)) {
			typeError(requiredType.name(), left, errorHandler);
			ok = false;
		}
		if (!right.isSame(requiredType)) {
			typeError(requiredType.name(), right, errorHandler);
			ok = false;
		}
		return ok ? requiredType : Imyhat.BAD;
	}

}
