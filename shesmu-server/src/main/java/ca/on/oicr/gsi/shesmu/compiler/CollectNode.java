package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Parser.Rule;

public abstract class CollectNode {
	private interface DefaultContructor {
		CollectNode create(int line, int column, ExpressionNode selector, ExpressionNode alternative);
	}

	private static final Parser.ParseDispatch<CollectNode> DISPATCH = new Parser.ParseDispatch<>();
	static {
		DISPATCH.addKeyword("List", (p, o) -> {
			o.accept(new CollectNodeCollect(p.line(), p.column()));
			return p;
		});
		DISPATCH.addKeyword("Count", (p, o) -> {
			o.accept(new CollectNodeCount(p.line(), p.column()));
			return p;
		});
		DISPATCH.addKeyword("First", withDefault(CollectNodeFirst::new));
		DISPATCH.addKeyword("Max", optima(true));
		DISPATCH.addKeyword("Min", optima(false));
		DISPATCH.addKeyword("Reduce", (p, o) -> {
			final AtomicReference<String> accumulatorName = new AtomicReference<>();
			final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
			final AtomicReference<ExpressionNode> initialExpression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.symbol("(")//
					.whitespace()//
					.identifier(accumulatorName::set)//
					.whitespace()//
					.symbol("=")//
					.whitespace()//
					.then(ExpressionNode::parse, initialExpression::set)//
					.symbol(")")//
					.whitespace()//
					.then(ExpressionNode::parse, defaultExpression::set);
			if (result.isGood()) {
				o.accept(new CollectNodeReduce(p.line(), p.column(), accumulatorName.get(), defaultExpression.get(),
						initialExpression.get()));
			}
			return result;
		});
	}

	private static Rule<CollectNode> optima(boolean max) {
		return withDefault((l, c, s, a) -> new CollectNodeOptima(l, c, max, s, a));
	}

	public static Parser parse(Parser parser, Consumer<CollectNode> output) {
		return parser.dispatch(DISPATCH, output);
	}

	private static Rule<CollectNode> withDefault(DefaultContructor constructor) {
		return (p, o) -> {
			final AtomicReference<ExpressionNode> selectExpression = new AtomicReference<>();
			final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.then(ExpressionNode::parse, selectExpression::set)//
					.keyword("Default")//
					.whitespace()//
					.then(ExpressionNode::parse, defaultExpression::set);
			if (result.isGood()) {
				o.accept(constructor.create(p.line(), p.column(), selectExpression.get(), defaultExpression.get()));
			}
			return result;
		};
	}

	private final int column;

	private final int line;

	protected CollectNode(int line, int column) {
		this.line = line;
		this.column = column;
	}

	/**
	 * Add all free variable names to the set provided.
	 *
	 * @param names
	 */
	public abstract void collectFreeVariables(Set<String> names);

	public int column() {
		return column;
	}

	public int line() {
		return line;
	}

	public abstract void render(JavaStreamBuilder builder);

	/**
	 * Resolve all variable definitions in this expression and its children.
	 */
	public abstract boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve all functions definitions in this expression
	 */
	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler);

	public abstract Imyhat type();

	public abstract boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler);

}
