package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public abstract class CollectNodeWithDefault extends CollectNode {
	protected final ExpressionNode alternative;
	private String name;
	private final Target parameter = new Target() {

		@Override
		public Flavour flavour() {
			return Flavour.LAMBDA;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Imyhat type() {
			return type;
		}
	};

	protected final ExpressionNode selector;
	private final String syntax;
	private Imyhat type;

	protected CollectNodeWithDefault(String syntax, int line, int column, ExpressionNode selector,
			ExpressionNode alternative) {
		super(line, column);
		this.syntax = syntax;
		this.selector = selector;
		this.alternative = alternative;

	}

	/**
	 * Add all free variable names to the set provided.
	 *
	 * @param names
	 */
	@Override
	public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		alternative.collectFreeVariables(names, predicate);
		final boolean remove = !names.contains(name);
		selector.collectFreeVariables(names, predicate);
		if (remove) {
			names.remove(name);
		}
	}

	protected abstract void finishMethod(Renderer renderer);

	protected final Imyhat incomingType() {
		return type;
	}

	protected abstract Pair<Renderer, Renderer> makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables);

	protected final String name() {
		return name;
	}

	@Override
	public final void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		collectFreeVariables(freeVariables, Flavour::needsCapture);
		final Pair<Renderer, Renderer> renderers = makeMethod(builder, builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));

		renderers.first().methodGen().visitCode();
		selector.render(renderers.first());
		finishMethod(renderers.first());
		renderers.first().methodGen().returnValue();
		renderers.first().methodGen().visitMaxs(0, 0);
		renderers.first().methodGen().visitEnd();

		renderers.second().methodGen().visitCode();
		alternative.render(renderers.second());
		renderers.second().methodGen().returnValue();
		renderers.second().methodGen().visitMaxs(0, 0);
		renderers.second().methodGen().visitEnd();
	}

	/**
	 * Resolve all variable definitions in this expression and its children.
	 */
	@Override
	public final boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return alternative.resolve(defs, errorHandler) & selector.resolve(defs.bind(parameter), errorHandler);
	}

	/**
	 * Resolve all functions definitions in this expression
	 */
	@Override
	public final boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return alternative.resolveFunctions(definedFunctions, errorHandler)
				& selector.resolveFunctions(definedFunctions, errorHandler);
	}

	protected abstract Imyhat returnType(Imyhat incomingType, Imyhat selectorType);

	@Override
	public final Imyhat type() {
		return alternative.type();
	}

	@Override
	public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		type = incoming;
		if (selector.typeCheck(errorHandler) & alternative.typeCheck(errorHandler)) {
			final Imyhat returnType = returnType(incoming, selector.type());
			if (returnType.isSame(alternative.type())) {
				return typeCheckExtra(errorHandler);
			} else {
				errorHandler.accept(
						String.format("%d:%d: %s would return %s, but default value is %s. They must be the same",
								line(), column(), syntax, returnType.name(), alternative.type().name()));
			}

		}
		return false;
	}

	/**
	 * Perform type checking on this expression.
	 *
	 * @param errorHandler
	 */
	protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);

}
