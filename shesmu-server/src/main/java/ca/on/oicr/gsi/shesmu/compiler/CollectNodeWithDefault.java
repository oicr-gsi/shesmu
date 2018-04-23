package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;

public abstract class CollectNodeWithDefault extends CollectNode {
	protected final ExpressionNode expression;

	protected CollectNodeWithDefault(int line, int column, ExpressionNode expression) {
		super(line, column);
		this.expression = expression;

	}

	/**
	 * Add all free variable names to the set provided.
	 *
	 * @param names
	 */
	@Override
	public final void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);
		collectFreeVariablesExtra(names);
	}

	protected abstract void collectFreeVariablesExtra(Set<String> names);

	protected abstract void finishMethod(Renderer renderer);

	protected abstract Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables);

	@Override
	public final void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		collectFreeVariables(freeVariables);
		final Renderer method = makeMethod(builder, builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));

		method.methodGen().visitCode();
		expression.render(method);
		finishMethod(method);
		method.methodGen().returnValue();
		method.methodGen().visitMaxs(0, 0);
		method.methodGen().visitEnd();
	}

	/**
	 * Resolve all variable definitions in this expression and its children.
	 */
	@Override
	public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler) & resolveExtra(defs, errorHandler);
	}

	protected abstract boolean resolveExtra(NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve all lookup definitions in this expression
	 */
	@Override
	public final boolean resolveLookups(Function<String, LookupDefinition> definedLookups,
			Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler)
				& resolveLookupsExtra(definedLookups, errorHandler);
	}

	protected abstract boolean resolveLookupsExtra(Function<String, LookupDefinition> definedLookups,
			Consumer<String> errorHandler);

	@Override
	public final Imyhat type() {
		return expression.type();
	}

	@Override
	public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler) && typeCheckExtra(incoming, errorHandler);
	}

	/**
	 * Perform type checking on this expression.
	 *
	 * @param errorHandler
	 */
	protected abstract boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler);

}
