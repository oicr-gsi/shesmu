package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeInteger extends ExpressionNode {

	private final long value;

	public ExpressionNodeInteger(int line, int column, long value) {
		super(line, column);
		this.value = value;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// Do nothing.
	}

	@Override
	public void render(Renderer renderer) {
		renderer.mark(line());

		renderer.methodGen().push(value);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Imyhat type() {
		return Imyhat.INTEGER;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return true;
	}

}
