package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ListNodeWithExpression extends ListNode {

	protected final ExpressionNode expression;

	private Imyhat incomingType;

	private String name;

	protected final Target parameter = new Target() {

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
			return incomingType;
		}

	};
	
	protected ListNodeWithExpression(int line, int column, ExpressionNode expression) {
		super(line, column);
		this.expression = expression;

	}

	/**
	 * Add all free variable names to the set provided.
	 *
	 * @param names
	 */
	public final void collectFreeVariables(Set<String> names) {
		final boolean remove = !names.contains(name);
		expression.collectFreeVariables(names);
		if (remove) {
			names.remove(name);
		}
	}

	protected abstract void finishMethod(Renderer renderer);

	protected abstract Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables);

	public final String name() {
		return name;
	}

	public abstract String nextName();

	/**
	 * The type of the returned stream
	 *
	 * This should return {@link Imyhat#BAD} if no type can be determined
	 */
	public abstract Imyhat nextType();

	public abstract Ordering order(Ordering previous, Consumer<String> errorHandler);

	public final void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		collectFreeVariables(freeVariables);
		final Renderer method = makeMethod(builder, builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name()) && !name.equals(v.name())).toArray(LoadableValue[]::new));

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
	public final Optional<String> resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return expression.resolve(defs.bind(parameter), errorHandler) ? Optional.of(nextName()) : Optional.empty();
	}

	/**
	 * Resolve all functions definitions in this expression
	 */
	public final boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		incomingType = incoming;
		return expression.typeCheck(errorHandler) && typeCheckExtra(incoming, errorHandler);
	}

	/**
	 * Perform type checking on this expression.
	 *
	 * @param errorHandler
	 */
	protected abstract boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler);

}
