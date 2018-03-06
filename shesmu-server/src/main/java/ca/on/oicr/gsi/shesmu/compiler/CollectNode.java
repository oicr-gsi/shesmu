package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.compiler.Parser.Rule;

public abstract class CollectNode {
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
		DISPATCH.addKeyword("First", (p, o) -> {
			final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
			final Parser result = p.whitespace().then(ExpressionNode::parse, defaultExpression::set);
			if (result.isGood()) {
				o.accept(new CollectNodeFirst(p.line(), p.column(), defaultExpression.get()));
			}
			return result;
		});
		DISPATCH.addKeyword("Max", optima(true));
		DISPATCH.addKeyword("Min", optima(false));
		DISPATCH.addKeyword("Reduce", (p, o) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<String> accumulatorName = new AtomicReference<>();
			final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
			final AtomicReference<ExpressionNode> initialExpression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.symbol("(")//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol(",")//
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
				o.accept(new CollectNodeReduce(p.line(), p.column(), name.get(), accumulatorName.get(),
						defaultExpression.get(), initialExpression.get()));
			}
			return result;
		});
	}

	private static Rule<CollectNode> optima(boolean max) {
		return (p, o) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<ExpressionNode> selectExpression = new AtomicReference<>();
			final AtomicReference<ExpressionNode> defaultExpression = new AtomicReference<>();
			final Parser result = p.whitespace()//
					.symbol("(")//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol(")").whitespace()//
					.then(ExpressionNode::parse, selectExpression::set)//
					.keyword("Default")//
					.whitespace()//
					.then(ExpressionNode::parse, defaultExpression::set);
			if (result.isGood()) {
				o.accept(new CollectNodeOptima(p.line(), p.column(), max, name.get(), selectExpression.get(),
						defaultExpression.get()));
			}
			return result;
		};
	}

	public static Parser parse(Parser parser, Consumer<CollectNode> output) {
		return parser.dispatch(DISPATCH, output);
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
	public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve all lookup definitions in this expression
	 */
	public abstract boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler);

	public abstract Imyhat type();

	public abstract boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler);

}
