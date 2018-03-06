package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public abstract class ListNode {
	private interface ListNodeConstructor {
		public ListNode build(int line, int column, String parameter, ExpressionNode expression);
	}

	private static final Parser.ParseDispatch<ListNode> DISPATCH = new Parser.ParseDispatch<>();
	static {
		DISPATCH.addKeyword("Map", handler(ListNodeMap::new));
		DISPATCH.addKeyword("Flatten", handler(ListNodeFlatten::new));
		DISPATCH.addKeyword("Filter", handler(ListNodeFilter::new));
		DISPATCH.addKeyword("Sort", handler(ListNodeSort::new));
	}

	private static Parser.Rule<ListNode> handler(ListNodeConstructor constructor) {
		return (p, o) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.symbol("(")//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol(")")//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set);
			if (result.isGood()) {
				o.accept(constructor.build(p.line(), p.column(), name.get(), expression.get()));
			}
			return result;
		};
	}

	public static Parser parse(Parser parser, Consumer<ListNode> output) {
		return parser.dispatch(DISPATCH, output);
	}

	private final int column;

	protected final ExpressionNode expression;

	private Imyhat incomingType;

	private final int line;

	protected final String name;

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

	protected ListNode(int line, int column, String name, ExpressionNode expression) {
		this.line = line;
		this.column = column;
		this.name = name;
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

	public int column() {
		return column;
	}

	protected abstract void finishMethod(Renderer renderer);

	public int line() {
		return line;
	}

	protected abstract Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables);

	/**
	 * The type of the returned stream
	 *
	 * This should return {@link Imyhat#BAD} if no type can be determined
	 */
	public abstract Imyhat nextType();

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
	public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs.bind(parameter), errorHandler);
	}

	/**
	 * Resolve all lookup definitions in this expression
	 */
	public final boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);
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
