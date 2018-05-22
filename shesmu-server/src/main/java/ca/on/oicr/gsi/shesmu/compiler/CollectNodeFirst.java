package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;

public class CollectNodeFirst extends CollectNodeWithDefault {

	protected CollectNodeFirst(int line, int column, ExpressionNode selector, ExpressionNode alternative) {
		super(line, column, selector, alternative);
	}

	@Override
	protected boolean checkConsistent(Imyhat incomingType, Imyhat selectorType, Imyhat alternativeType) {
		return selectorType.isSame(alternativeType);
	}

	@Override
	protected void finishMethod(Renderer renderer) {
	}

	@Override
	protected Pair<Renderer, Renderer> makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		final Renderer map = builder.map(name(), type().asmType(), loadables);
		final Renderer alternative = builder.first(type().asmType(), loadables);
		return new Pair<>(map, alternative);
	}

	@Override
	protected boolean typeCheckExtra(Consumer<String> errorHandler) {
		return true;
	}

}
