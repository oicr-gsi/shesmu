package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;

public class ExpressionNodeLogicalNot extends ExpressionNode {
	private final ExpressionNode inner;

	public ExpressionNodeLogicalNot(int line, int column, ExpressionNode inner) {
		super(line, column);
		this.inner = inner;

	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		inner.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		inner.render(renderer);
		renderer.methodGen().not();
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return inner.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, LookupDefinition> definedLookups, Consumer<String> errorHandler) {
		return inner.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.BOOLEAN;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean ok = inner.typeCheck(errorHandler);
		if (ok) {
			ok = inner.type().isSame(Imyhat.BOOLEAN);
			if (!ok) {
				typeError("boolean", inner.type(), errorHandler);
			}
		}
		return ok;
	}

}
