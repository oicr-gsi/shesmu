package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public final class ExpressionNodeBinary extends ExpressionNode {

	private final ExpressionNode left;
	private ArithmeticOperation operation = ArithmeticOperation.BAD;
	private final Supplier<Stream<ArithmeticOperation>> operations;
	private final ExpressionNode right;
	private final String symbol;

	public ExpressionNodeBinary(Supplier<Stream<ArithmeticOperation>> operations, String symbol, int line, int column,
			ExpressionNode left, ExpressionNode right) {
		super(line, column);
		this.operations = operations;
		this.symbol = symbol;
		this.left = left;
		this.right = right;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		left.collectFreeVariables(names, predicate);
		right.collectFreeVariables(names, predicate);
	}

	public ExpressionNode left() {
		return left;
	}

	@Override
	public void render(Renderer renderer) {
		operation.render(renderer, left::render, right::render);
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
		return operation.returnType();
	}

	@Override
	public final boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = left.typeCheck(errorHandler) & right.typeCheck(errorHandler);
		if (ok) {
			final Optional<ArithmeticOperation> operation = operations.get()//
					.filter(op -> op.leftType().isSame(left.type()) && op.rightType().isSame(right.type()))//
					.findFirst();
			if (operation.isPresent()) {
				this.operation = operation.get();
				return true;
			}
			errorHandler.accept(String.format("%d:%d: No operation %s %s %s is defined.", line(), column(),
					left.type().name(), symbol, right.type().name()));
		}
		return false;
	}

}
