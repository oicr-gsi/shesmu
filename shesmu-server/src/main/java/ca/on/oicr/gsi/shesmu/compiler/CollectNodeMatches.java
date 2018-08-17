package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.JavaStreamBuilder.Match;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public final class CollectNodeMatches extends CollectNode {

	private final Match matchType;
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
	private final ExpressionNode selector;

	private Imyhat type;

	public CollectNodeMatches(int line, int column, Match matchType, ExpressionNode selector) {
		super(line, column);
		this.matchType = matchType;
		this.selector = selector;
	}

	@Override
	public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		final boolean removeName = !names.contains(name);
		selector.collectFreeVariables(names, predicate);
		if (removeName) {
			names.remove(name);
		}
	}

	@Override
	public final void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		selector.collectFreeVariables(freeVariables, Flavour::needsCapture);
		freeVariables.remove(name);
		final Renderer renderer = builder.match(matchType, name, builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		selector.render(renderer);
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
	}

	@Override
	public final boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return selector.resolve(defs.bind(parameter), errorHandler);
	}

	@Override
	public final boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return selector.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public final Imyhat type() {
		return Imyhat.BOOLEAN;
	}

	@Override
	public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		type = incoming;
		if (selector.typeCheck(errorHandler)) {
			if (!selector.type().isSame(Imyhat.BOOLEAN)) {
				errorHandler.accept(String.format("%d:%d: Boolean value expected in %s, but got %s.", line(), column(),
						matchType.syntax(), selector.type().name()));
				return false;
			}
			return true;
		}
		return false;
	}

}
