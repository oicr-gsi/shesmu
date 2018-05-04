package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeSort extends ListNode {
	public ListNodeSort(int line, int column, ExpressionNode expression) {
		super(line, column, expression);
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		renderer.methodGen().box(expression.type().asmType());
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.sort(name(), expression.type(), loadables);
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
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		if (expression.type().isSame(Imyhat.DATE) || expression.type().isSame(Imyhat.INTEGER)) {
			return true;
		}
		errorHandler.accept(String.format("%d:%d: Expected date or integer for sorting but got %s.", line(), column(),
				incoming.name()));
		return false;
	}

}
