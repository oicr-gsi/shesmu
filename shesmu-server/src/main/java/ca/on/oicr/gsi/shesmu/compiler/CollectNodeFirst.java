package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class CollectNodeFirst extends CollectNodeWithDefault {

	protected CollectNodeFirst(int line, int column, ExpressionNode expression) {
		super(line, column, expression);
	}

	@Override
	protected void collectFreeVariablesExtra(Set<String> names) {
		// No free variables.
	}

	@Override
	protected void finishMethod(Renderer renderer) {
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.first(type().asmType(), loadables);
	}

	@Override
	protected boolean resolveExtra(NameDefinitions defs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	protected boolean resolveLookupsExtra(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		if (incoming.isSame(type())) {
			return true;
		}
		errorHandler.accept(String.format("%d:%d: Iterating over %s, but default value is %s.", line(), column(),
				incoming.name(), type().name()));
		return false;
	}

}
