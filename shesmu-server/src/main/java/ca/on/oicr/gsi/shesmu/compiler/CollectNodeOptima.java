package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.Pair;

public final class CollectNodeOptima extends CollectNodeWithDefault {

	private final boolean max;
	private final String name;
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
	private final ExpressionNode selector;

	private Imyhat type;

	public CollectNodeOptima(int line, int column, boolean max, String name, ExpressionNode selector,
			ExpressionNode defaultExpression) {
		super(line, column, defaultExpression);
		this.max = max;
		this.name = name;
		this.selector = selector;
	}

	@Override
	protected void collectFreeVariablesExtra(Set<String> names) {
		final boolean remove = !names.contains(name);
		selector.collectFreeVariables(names);
		if (remove) {
			names.remove(name);
		}
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		// Do nothing.
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		final Set<String> freeVariables = new HashSet<>();
		collectFreeVariables(freeVariables);
		final Pair<Renderer, Renderer> renderers = builder.optima(max, name, selector.type(),
				builder.renderer().allValues().filter(v -> freeVariables.contains(v.name()) && !name.equals(v.name()))
						.toArray(LoadableValue[]::new));
		renderers.first().methodGen().visitCode();
		selector.render(renderers.first());
		renderers.first().methodGen().box(selector.type().asmType());
		renderers.first().methodGen().returnValue();
		renderers.first().methodGen().visitMaxs(0, 0);
		renderers.first().methodGen().visitEnd();
		return renderers.second();
	}

	@Override
	protected boolean resolveExtra(NameDefinitions defs, Consumer<String> errorHandler) {
		return selector.resolve(defs.bind(parameter), errorHandler);
	}

	@Override
	protected boolean resolveLookupsExtra(Function<String, LookupDefinition> definedLookups,
			Consumer<String> errorHandler) {
		return selector.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		type = incoming;
		boolean ok = selector.typeCheck(errorHandler);
		if (ok) {
			ok = selector.type().isSame(Imyhat.INTEGER) || selector.type().isSame(Imyhat.DATE);
			if (!ok) {
				errorHandler.accept(String.format("%d:%d: Expected date or integer, but got %s.", line(), column(),
						selector.type()));
			}
		}
		return ok;
	}

}
