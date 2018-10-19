package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Parser.Rule;

/**
 * A collection action in a “Group” clause
 *
 * Also usable as the variable definition for the result
 */
public abstract class GroupNode extends DefinedTarget {
	private interface ParseGroup {
		GroupNode make(int line, int column, String name);

	}

	private interface ParseGroupWithExpression {
		GroupNode make(int line, int column, String name, ExpressionNode expression);

	}

	private interface ParseGroupWithExpressionDefaultable {
		GroupNodeDefaultable make(int line, int column, String name, ExpressionNode expression);

	}

	private static final Parser.ParseDispatch<ParseGroup> GROUPERS = new Parser.ParseDispatch<>();

	static {
		GROUPERS.addKeyword("Count", (p, o) -> {
			o.accept(GroupNodeCount::new);
			return p;
		});
		GROUPERS.addKeyword("First", ofWithDefault(GroupNodeFirst::new));
		GROUPERS.addKeyword("List", of(GroupNodeList::new));
		GROUPERS.addKeyword("PartitionCount", of(GroupNodePartitionCount::new));
		GROUPERS.addKeyword("Max", ofWithDefault(
				(line, column, name, expression) -> new GroupNodeOptima(line, column, name, expression, true)));
		GROUPERS.addKeyword("Min", ofWithDefault(
				(line, column, name, expression) -> new GroupNodeOptima(line, column, name, expression, false)));
		for (final Match match : Match.values()) {
			GROUPERS.addKeyword(match.syntax(), of(
					(line, column, name, expression) -> new GroupNodeMatches(line, column, name, match, expression)));
		}
		GROUPERS.addKeyword("Where", (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final AtomicReference<ParseGroup> sink = new AtomicReference<>();
			final Parser intermediate = p//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set)//
					.whitespace();
			final Parser result = intermediate//
					.dispatch(GROUPERS, sink::set)//
					.whitespace();
			if (result.isGood()) {
				o.accept((line, column, name) -> new GroupNodeWhere(line, column, expression.get(),
						sink.get().make(intermediate.line(), intermediate.column(), name)));
			}
			return result;
		});
	}

	private static final Rule<ParseGroup> of(ParseGroupWithExpression maker) {
		return (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set)//
					.whitespace();
			if (result.isGood()) {
				o.accept((line, column, name) -> maker.make(line, column, name, expression.get()));
			}
			return result;
		};
	}

	private static final Rule<ParseGroup> ofWithDefault(ParseGroupWithExpressionDefaultable maker) {
		return (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set)//
					.whitespace();
			if (result.isGood()) {
				final Parser defaultResult = result//
						.keyword("Default");//
				if (defaultResult.isGood()) {
					final AtomicReference<ExpressionNode> initial = new AtomicReference<>();
					final Parser defaultComplete = defaultResult//
							.whitespace()//
							.then(ExpressionNode::parse, initial::set)//
							.whitespace();
					if (defaultComplete.isGood()) {
						o.accept((line, column, name) -> new GroupNodeWithDefault(line, column, initial.get(),
								maker.make(line, column, name, expression.get())));
					}
					return defaultComplete;
				}
				o.accept((line, column, name) -> maker.make(line, column, name, expression.get()));
			}
			return result;
		};
	}

	public static Parser parse(Parser input, Consumer<GroupNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();

		final Parser result = input//
				.whitespace()//
				.identifier(name::set)//
				.whitespace()//
				.keyword("=")//
				.whitespace()//
				.dispatch(GROUPERS, maker -> output.accept(maker.make(input.line(), input.column(), name.get())));
		return result;

	}

	private final int column;
	private final int line;

	public GroupNode(int line, int column) {
		this.line = line;
		this.column = column;
	}

	public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

	public final int column() {
		return column;
	}

	@Override
	public final Flavour flavour() {
		return Flavour.STREAM;
	}

	public final int line() {
		return line;
	}

	public abstract void render(Regrouper regroup, RootBuilder builder);

	public abstract boolean resolve(NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler);

	public abstract boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler);

	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
