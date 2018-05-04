package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ExpressionNodeBinary extends ExpressionNode {

	private final ExpressionNode left;
	private final ExpressionNode right;

	private Imyhat type = Imyhat.BAD;

	public ExpressionNodeBinary(int line, int column, ExpressionNode left, ExpressionNode right) {
		super(line, column);
		this.left = left;
		this.right = right;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		left.collectFreeVariables(names);
		right.collectFreeVariables(names);
	}

	public ExpressionNode left() {
		return left;
	}

	@Override
	public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
	}

	@Override
	public final boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return left.resolveFunctions(definedFunctions, errorHandler)
				& right.resolveFunctions(definedFunctions, errorHandler);
	}

	public ExpressionNode right() {
		return right;
	}

	@Override
	public final Imyhat type() {
		return type;
	}

	@Override
	public final boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = left.typeCheck(errorHandler) & right.typeCheck(errorHandler);
		if (ok) {
			type = typeCheck(left.type(), right.type(), errorHandler);
			return !type.isBad();
		}
		return ok;
	}

	protected abstract Imyhat typeCheck(Imyhat left, Imyhat right, Consumer<String> errorHandler);

}
