package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Label;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;

public class ExpressionNodeComparison extends ExpressionNode {

	private final Comparison comparison;
	private final ExpressionNode left;
	private final ExpressionNode right;

	public ExpressionNodeComparison(int line, int column, Comparison comparison, ExpressionNode left,
			ExpressionNode right) {
		super(line, column);
		this.comparison = comparison;
		this.left = left;
		this.right = right;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		left.collectFreeVariables(names);
		right.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		left.render(renderer);
		right.render(renderer);
		renderer.mark(line());

		final Label end = renderer.methodGen().newLabel();
		final Label truePath = renderer.methodGen().newLabel();
		if (left.type().isSame(Imyhat.BOOLEAN)) {
			comparison.branchBool(truePath, renderer.methodGen());
		} else if (left.type().isSame(Imyhat.INTEGER)) {
			comparison.branchInt(truePath, renderer.methodGen());
		} else if (left.type().isSame(Imyhat.DATE)) {
			comparison.branchDate(truePath, renderer.methodGen());
		} else {
			comparison.branchObject(truePath, renderer.methodGen());
		}
		renderer.methodGen().push(false);
		renderer.methodGen().goTo(end);
		renderer.methodGen().mark(truePath);
		renderer.methodGen().push(true);
		renderer.methodGen().mark(end);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, LookupDefinition> definedLookups, Consumer<String> errorHandler) {
		return left.resolveLookups(definedLookups, errorHandler) & right.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.BOOLEAN;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = left.typeCheck(errorHandler) & right.typeCheck(errorHandler);
		if (ok) {
			if (!left.type().isSame(right.type())) {
				typeError(left.type().name(), right.type(), errorHandler);
				return false;
			}
			if (comparison.isOrdered() && !left.type().isOrderable()) {
				errorHandler.accept(String.format("%d:%d: Comparison %s not defined for type %s.", line(), column(),
						comparison.symbol(), left.type().name()));
				return false;
			}
		}
		return ok;
	}

}
