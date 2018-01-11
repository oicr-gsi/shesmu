package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeMap extends ListNode {

	public ListNodeMap(int line, int column, String name, ExpressionNode expression) {
		super(line, column, name, expression);
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		// Do nothing.
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.map(name, expression.type().asmType(), loadables);
	}

	@Override
	public Imyhat nextType() {
		return expression.type();
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		return true;
	}

}
