package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;

public final class CollectNodeOptima extends CollectNodeWithDefault {

	private final boolean max;

	public CollectNodeOptima(int line, int column, boolean max, ExpressionNode selector, ExpressionNode alternate) {
		super(line, column, selector, alternate);
		this.max = max;
	}

	@Override
	protected boolean checkConsistent(Imyhat incomingType, Imyhat selectorType, Imyhat alternativeType) {
		return incomingType.isSame(alternativeType);
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		renderer.methodGen().box(selector.type().asmType());
	}

	@Override
	protected Pair<Renderer, Renderer> makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.optima(max, name(), selector.type(), loadables);
	}

	@Override
	protected boolean typeCheckExtra(Consumer<String> errorHandler) {
		if (selector.type().isSame(Imyhat.INTEGER) || type().isSame(Imyhat.DATE)) {
			return true;
		}
		errorHandler
				.accept(String.format("%d:%d: Expected date or integer, but got %s.", line(), column(), type().name()));
		return false;
	}

}
