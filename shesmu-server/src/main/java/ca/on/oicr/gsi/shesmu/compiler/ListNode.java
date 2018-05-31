package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ListNode {
	private interface ListNodeConstructor {
		public ListNode build(int line, int column, ExpressionNode expression);
	}
	private interface ListNodeNamedConstructor {
		public ListNode build(int line, int column, String name, ExpressionNode expression);
	}

	public enum Ordering {
		BAD, RANDOM, REQESTED
	}

	private static final Parser.ParseDispatch<ListNode> DISPATCH = new Parser.ParseDispatch<>();
	static {
		DISPATCH.addKeyword("Let", handler(ListNodeMap::new, p -> p.symbol("=")));
		DISPATCH.addKeyword("Flatten", handler(ListNodeFlatten::new, p -> p.keyword("In")));
		DISPATCH.addKeyword("Where", handler(ListNodeFilter::new));
		DISPATCH.addKeyword("Sort", handler(ListNodeSort::new));
		DISPATCH.addKeyword("Reverse", (p, o) -> {
			o.accept(new ListNodeReverse(p.line(), p.column()));
			return p.whitespace();
		});
	}

	private static Parser.Rule<ListNode> handler(ListNodeConstructor constructor) {
		return (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.then(ExpressionNode::parse, expression::set);
			if (result.isGood()) {
				o.accept(constructor.build(p.line(), p.column(), expression.get()));
			}
			return result;
		};
	}

	private static Parser.Rule<ListNode> handler(ListNodeNamedConstructor constructor,
			Function<Parser, Parser> linker) {
		return (p, o) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = linker.apply(p.whitespace()//
					.identifier(name::set)//
					.whitespace())//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set);
			if (result.isGood()) {
				o.accept(constructor.build(p.line(), p.column(), name.get(), expression.get()));
			}
			return result;
		};
	}

	public static Parser parse(Parser parser, Consumer<ListNode> output) {
		return parser.whitespace().dispatch(DISPATCH, output).whitespace();
	}

	private final int column;

	private final int line;
	
	protected ListNode(int line, int column) {
		this.line = line;
		this.column = column;

	}

	public int column() {
		return column;
	}

	public int line() {
		return line;
	}

	public abstract String name() ;

	public abstract String nextName();

	/**
	 * The type of the returned stream
	 *
	 * This should return {@link Imyhat#BAD} if no type can be determined
	 */
	public abstract Imyhat nextType();

	public abstract Ordering order(Ordering previous, Consumer<String> errorHandler);

	public abstract void render(JavaStreamBuilder builder) ;
	/**
	 * Resolve all variable definitions in this expression and its children.
	 */
	public abstract Optional<String> resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) ;

	/**
	 * Resolve all functions definitions in this expression
	 */
	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) ;

	public abstract boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler);

	public abstract void collectFreeVariables(Set<String> names) ;


}
