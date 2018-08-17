package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public abstract class SourceNode {

	private static final Parser.ParseDispatch<SourceNode> DISPATCH = new Parser.ParseDispatch<>();
	static {
		DISPATCH.addKeyword("In", (p, o) -> {
			final AtomicReference<ExpressionNode> source = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, source::set)//
					.whitespace();
			if (result.isGood()) {
				o.accept(new SourceNodeSet(p.line(), p.column(), source.get()));
			}
			return result;
		});
		DISPATCH.addKeyword("Splitting", (p, o) -> {
			final AtomicReference<ExpressionNode> source = new AtomicReference<>();
			final AtomicReference<String> regex = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, source::set)//
					.whitespace()//
					.keyword("By")//
					.whitespace()//
					.regex(ExpressionNode.REGEX, m -> regex.set(m.group(1)), "Regular expression.")//
					.whitespace();
			if (result.isGood()) {
				o.accept(new SourceNodeSplit(p.line(), p.column(), regex.get(), source.get()));
			}
			return result;
		});
		DISPATCH.addKeyword("From", (p, o) -> {
			final AtomicReference<ExpressionNode> start = new AtomicReference<>();
			final AtomicReference<ExpressionNode> end = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, start::set)//
					.whitespace()//
					.keyword("To")//
					.whitespace()//
					.then(ExpressionNode::parse, end::set)//
					.whitespace();
			if (result.isGood()) {
				o.accept(new SourceNodeRange(p.line(), p.column(), start.get(), end.get()));
			}
			return result;
		});
	}

	public static Parser parse(Parser input, Consumer<SourceNode> output) {
		return input.dispatch(DISPATCH, output);

	}

	private final int column;

	private final int line;

	public SourceNode(int line, int column) {
		super();
		this.line = line;
		this.column = column;
	}

	/**
	 * Add all free variable names to the set provided.
	 *
	 * @param names
	 */
	public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

	public int column() {
		return column;
	}

	public int line() {
		return line;
	}

	public abstract Ordering ordering();

	/**
	 * Produce bytecode for this expression
	 */
	public abstract JavaStreamBuilder render(Renderer renderer);

	/**
	 * Resolve all variable definitions in this expression and its children.
	 */
	public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve all function definitions in this expression
	 */
	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler);

	/**
	 * The type of this expression
	 *
	 * This should return {@link Imyhat#BAD} if no type can be determined
	 */
	public abstract Imyhat streamType();

	/**
	 * Perform type checking on this expression and its children.
	 *
	 * @param errorHandler
	 * @return
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
