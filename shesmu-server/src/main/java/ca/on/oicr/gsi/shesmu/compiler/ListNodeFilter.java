package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeFilter extends ListNodeWithExpression {

	public ListNodeFilter(int line, int column, ExpressionNode expression) {
		super(line, column, expression);
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		// Do nothing.
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.filter(line(), column(), name(), loadables);
	}

	@Override
	public String nextName() {
		return name();
	}

	@Override
	public Imyhat nextType() {
		return parameter.type();
	}

	@Override
	public Ordering order(Ordering previous, Consumer<String> errorHandler) {
		return previous;
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		if (expression.type().isSame(Imyhat.BOOLEAN)) {
			return true;
		} else {
			errorHandler.accept(String.format("%d:%d: Filter expression must be boolean, but got %s.", line(), column(),
					expression.type().name()));
			return false;
		}
	}

}
