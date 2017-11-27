package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public final class ExpressionNodeBoolean extends ExpressionNode {

	private final boolean value;

	public ExpressionNodeBoolean(int line, int column, boolean value) {
		super(line, column);
		this.value = value;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// Do nothing.
	}

	@Override
	public void render(Renderer renderer) {
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
		return Imyhat.BOOLEAN;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return true;
	}

}
