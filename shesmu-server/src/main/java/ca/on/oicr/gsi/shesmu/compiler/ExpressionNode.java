package ca.on.oicr.gsi.shesmu.compiler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;

/**
 * An expression in the Shesmu language
 */
public abstract class ExpressionNode {
	interface BinaryExpression {
		ExpressionNode create(int line, int column, ExpressionNode left, ExpressionNode right);
	}

	interface UnaryExpression {
		ExpressionNode create(int line, int column, ExpressionNode node);
	}

	private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> ARITHMETIC_CONJUNCTION = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> ARITHMETIC_DISJUNCTION = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> COMPARISON = new Parser.ParseDispatch<>();
	private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}))?");
	private static final Parser.ParseDispatch<Integer> INT_SUFFIX = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> LOGICAL_CONJUNCTION = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> LOGICAL_DISJUNCTION = new Parser.ParseDispatch<>();
	private static final Pattern REGEX = Pattern.compile("^/(([^/\n]|\\\\/)*)/");
	private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> SUFFIX = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<ExpressionNode> TERMINAL = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> UNARY = new Parser.ParseDispatch<>();

	static {
		INT_SUFFIX.addKeyword("Gi", just(1024 * 1024 * 1024));
		INT_SUFFIX.addKeyword("Mi", just(1024 * 1024));
		INT_SUFFIX.addKeyword("ki", just(1024));
		INT_SUFFIX.addKeyword("G", just(1000 * 1000 * 1000));
		INT_SUFFIX.addKeyword("M", just(1000 * 1000));
		INT_SUFFIX.addKeyword("k", just(1000));
		INT_SUFFIX.addKeyword("weeks", just(3600 * 24 * 7));
		INT_SUFFIX.addKeyword("days", just(3600 * 24));
		INT_SUFFIX.addKeyword("hours", just(3600));
		INT_SUFFIX.addKeyword("mins", just(60));
		INT_SUFFIX.addKeyword("", just(1));

		LOGICAL_DISJUNCTION.addSymbol("||", just(ExpressionNodeLogicalOr::new));

		LOGICAL_CONJUNCTION.addSymbol("&&", just(ExpressionNodeLogicalAnd::new));

		for (final Comparison comparison : Comparison.values()) {
			COMPARISON.addSymbol(comparison.symbol(), (p, o) -> {
				final AtomicReference<ExpressionNode> right = new AtomicReference<>();
				final Parser result = parse4(p.whitespace(), right::set).whitespace();
				if (result.isGood()) {

					o.accept(left -> new ExpressionNodeComparison(p.line(), p.column(), comparison, left, right.get()));
				}
				return result;
			});
		}
		COMPARISON.addSymbol("~", (p, o) -> {
			final AtomicReference<String> regex = new AtomicReference<>();
			final Parser result = p.whitespace().regex(REGEX, m -> regex.set(m.group(1)), "Regular expression.")
					.whitespace();
			if (result.isGood()) {
				o.accept(left -> new ExpressionNodeRegex(p.line(), p.column(), left, regex.get()));
			}
			return result;
		});
		ARITHMETIC_DISJUNCTION.addSymbol("+", just(ExpressionNodeArithmeticAdd::new));
		ARITHMETIC_DISJUNCTION.addSymbol("-", just(ExpressionNodeArithmeticSubtract::new));

		ARITHMETIC_CONJUNCTION.addSymbol("*", just(ExpressionNodeArithmeticMultiply::new));
		ARITHMETIC_CONJUNCTION.addSymbol("/", just(ExpressionNodeArithmeticDivide::new));
		ARITHMETIC_CONJUNCTION.addSymbol("%", just(ExpressionNodeArithmeticModulo::new));

		UNARY.addSymbol("!", just(ExpressionNodeLogicalNot::new));
		UNARY.addSymbol("-", just(ExpressionNodeNegate::new));

		SUFFIX.addKeyword("In", (p, o) -> {
			final AtomicReference<ExpressionNode> collection = new AtomicReference<>();
			final Parser result = parse8(p.whitespace(), collection::set);
			if (result.isGood()) {
				final ExpressionNode c = collection.get();
				o.accept(node -> new ExpressionNodeContains(p.line(), p.column(), node, c));
			}
			return result;
		});
		SUFFIX.addSymbol("[", (p, o) -> {
			final AtomicLong index = new AtomicLong();
			final Parser result = p.whitespace().integer(index::set, 10).whitespace().symbol("]");
			if (result.isGood()) {
				final int i = (int) index.get();
				o.accept(node -> new ExpressionNodeTupleGet(p.line(), p.column(), node, i));
			}
			return result;
		});

		TERMINAL.addKeyword("Date", (p, o) -> p.whitespace().regex(DATE, m -> {
			ZonedDateTime date;
			if (m.start(1) == m.end(1)) {
				date = LocalDate.parse(m.group(0)).atStartOfDay(ZoneId.of("Z"));
			} else if (m.start(2) == m.end(2)) {
				date = LocalDateTime.parse(m.group(0)).atZone(ZoneId.of("Z"));
			} else {
				date = ZonedDateTime.parse(m.group(0));
			}
			o.accept(new ExpressionNodeDate(p.line(), p.column(), date));
		}, "Expected date.").whitespace());
		TERMINAL.addSymbol("{", (p, o) -> {
			final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.list(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')//
					.whitespace()//
					.symbol("}")//
					.whitespace();
			if (p.isGood()) {
				o.accept(new ExpressionNodeTuple(p.line(), p.column(), items.get()));
			}
			return result;
		});
		TERMINAL.addSymbol("[", (p, o) -> {
			final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.list(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')//
					.whitespace()//
					.symbol("]")//
					.whitespace();
			if (p.isGood()) {
				o.accept(new ExpressionNodeList(p.line(), p.column(), items.get()));
			}
			return result;
		});
		TERMINAL.addSymbol("\"", (p, o) -> {
			final AtomicReference<List<StringNode>> items = new AtomicReference<>();
			final Parser result = p.list(items::set, StringNode::parse).symbol("\"").whitespace();
			if (p.isGood()) {
				o.accept(new ExpressionNodeString(p.line(), p.column(), items.get()));
			}
			return result;

		});
		TERMINAL.addSymbol("(", (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = parse(p.whitespace(), expression::set).whitespace().symbol(")").whitespace();
			if (result.isGood()) {
				o.accept(expression.get());
			}
			return result;
		});
		TERMINAL.addRaw("integer", (p, o) -> {
			final AtomicLong value = new AtomicLong();
			final AtomicInteger multiplier = new AtomicInteger();
			final Parser result = p.integer(value::set, 10).dispatch(INT_SUFFIX, multiplier::set);
			if (result.isGood()) {
				o.accept(new ExpressionNodeInteger(p.line(), p.column(), value.get() * multiplier.get()));
			}
			return result;
		});
		TERMINAL.addKeyword("True", (p, o) -> {
			o.accept(new ExpressionNodeBoolean(p.line(), p.column(), true));
			return p;
		});
		TERMINAL.addKeyword("False", (p, o) -> {
			o.accept(new ExpressionNodeBoolean(p.line(), p.column(), false));
			return p;
		});
		TERMINAL.addRaw("function call, variable", (p, o) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			Parser result = p.identifier(name::set).whitespace();
			if (result.isGood()) {
				if (result.lookAhead('(')) {
					final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
					result = result.symbol("(")//
							.whitespace()//
							.list(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')//
							.whitespace()//
							.symbol(")")//
							.whitespace();
					if (p.isGood()) {
						o.accept(new ExpressionNodeFunctionCall(p.line(), p.column(), name.get(), items.get()));
					}
				} else {
					o.accept(new ExpressionNodeVariable(p.line(), p.column(), name.get()));
				}
			}
			return result;
		});
	}

	private static Parser.Rule<BinaryOperator<ExpressionNode>> just(BinaryExpression creator) {
		return (p, o) -> {
			o.accept((l, r) -> creator.create(p.line(), p.column(), l, r));
			return p;
		};
	}

	private static <T> Parser.Rule<T> just(T value) {
		return (p, o) -> {
			o.accept(value);
			return p;
		};
	}

	private static Parser.Rule<UnaryOperator<ExpressionNode>> just(UnaryExpression creator) {
		return (p, o) -> {
			o.accept(n -> creator.create(p.line(), p.column(), n));
			return p;
		};
	}

	public static Parser parse(Parser input, Consumer<ExpressionNode> output) {
		final Parser switchParser = input.keyword("Switch");
		if (switchParser.isGood()) {
			final AtomicReference<List<Pair<ExpressionNode, ExpressionNode>>> cases = new AtomicReference<>();
			final AtomicReference<ExpressionNode> test = new AtomicReference<>();
			final AtomicReference<ExpressionNode> alternative = new AtomicReference<>();
			final Parser result = parse1(
					parse(switchParser.whitespace(), test::set).whitespace().list(cases::set, (cp, co) -> {
						final AtomicReference<ExpressionNode> condition = new AtomicReference<>();
						final AtomicReference<ExpressionNode> value = new AtomicReference<>();
						final Parser cresult = parse(parse(cp.whitespace().keyword("When").whitespace(), condition::set)
								.whitespace().keyword("Then").whitespace(), value::set);
						if (cresult.isGood()) {
							co.accept(new Pair<>(condition.get(), value.get()));
						}
						return cresult;
					}).whitespace().keyword("Else").whitespace(), alternative::set).whitespace();
			if (result.isGood()) {
				output.accept(new ExpressionNodeSwitch(input.line(), input.column(), test.get(), cases.get(),
						alternative.get()));
			}
			return result;
		}

		final Parser forParser = input.keyword("For");
		if (forParser.isGood()) {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<ExpressionNode> source = new AtomicReference<>();
			final AtomicReference<List<ListNode>> transforms = new AtomicReference<>();
			final AtomicReference<CollectNode> collector = new AtomicReference<>();
			final Parser result = forParser.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.keyword("In")//
					.whitespace()//
					.then(ExpressionNode::parse, source::set)//
					.whitespace()//
					.list(transforms::set, ListNode::parse)//
					.then(CollectNode::parse, collector::set)//
					.whitespace();
			if (result.isGood()) {
				output.accept(new ExpressionNodeListTransform(input.line(), input.column(), name.get(), source.get(),
						transforms.get(), collector.get()));
			}
			return result;
		}

		final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
		final Parser parserResult = parse1(input, expression::set);
		if (!parserResult.isGood()) {
			return parserResult;
		}
		final Parser ternaryParser = parserResult.symbol("?");
		if (ternaryParser.isGood()) {
			final AtomicReference<ExpressionNode> trueExpression = new AtomicReference<>();
			final AtomicReference<ExpressionNode> falseExpression = new AtomicReference<>();
			final Parser result = ternaryParser.whitespace() //
					.then(ExpressionNode::parse1, trueExpression::set) //
					.symbol(":") //
					.whitespace() //
					.then(ExpressionNode::parse1, falseExpression::set);
			if (result.isGood()) {
				output.accept(new ExpressionNodeTernaryIf(input.line(), input.column(), expression.get(),
						trueExpression.get(), falseExpression.get()));
			}
			return result;
		} else {
			output.accept(expression.get());
			return parserResult;
		}
	}

	private static Parser parse1(Parser input, Consumer<ExpressionNode> output) {
		return scanBinary(ExpressionNode::parse2, LOGICAL_DISJUNCTION, input, output);
	}

	private static Parser parse2(Parser input, Consumer<ExpressionNode> output) {
		return scanBinary(ExpressionNode::parse3, LOGICAL_CONJUNCTION, input, output);
	}

	private static Parser parse3(Parser input, Consumer<ExpressionNode> output) {
		return scanSuffixed(ExpressionNode::parse4, COMPARISON, input, output);
	}

	private static Parser parse4(Parser input, Consumer<ExpressionNode> output) {
		return scanBinary(ExpressionNode::parse5, ARITHMETIC_DISJUNCTION, input, output);
	}

	private static Parser parse5(Parser input, Consumer<ExpressionNode> output) {
		return scanBinary(ExpressionNode::parse6, ARITHMETIC_CONJUNCTION, input, output);
	}

	private static Parser parse6(Parser input, Consumer<ExpressionNode> output) {
		return scanPrefixed(ExpressionNode::parse7, UNARY, input, output);
	}

	private static Parser parse7(Parser input, Consumer<ExpressionNode> output) {
		return scanSuffixed(ExpressionNode::parse8, SUFFIX, input, output);
	}

	private static Parser parse8(Parser input, Consumer<ExpressionNode> output) {
		return input.dispatch(TERMINAL, output);
	}

	private static <T> Parser scanBinary(Parser.Rule<T> child, Parser.ParseDispatch<BinaryOperator<T>> condensers,
			Parser input, Consumer<T> output) {
		final AtomicReference<T> node = new AtomicReference<>();
		Parser parser = child.parse(input, node::set);
		while (parser.isGood()) {
			final AtomicReference<T> right = new AtomicReference<>();
			final AtomicReference<BinaryOperator<T>> condenser = new AtomicReference<>();
			final Parser next = child.parse(parser.dispatch(condensers, condenser::set).whitespace(), right::set);
			if (next.isGood()) {
				node.set(condenser.get().apply(node.get(), right.get()));
				parser = next;
			} else {
				output.accept(node.get());
				return parser;
			}
		}
		return parser;
	}

	private static <T> Parser scanPrefixed(Parser.Rule<T> child, Parser.ParseDispatch<UnaryOperator<T>> condensers,
			Parser input, Consumer<T> output) {
		final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
		Parser next = input.dispatch(condensers, modifier::set).whitespace();
		if (!next.isGood()) {
			next = input;
			modifier.set(UnaryOperator.identity());
		}
		final AtomicReference<T> node = new AtomicReference<>();
		final Parser result = child.parse(next, node::set).whitespace();
		if (result.isGood()) {
			output.accept(modifier.get().apply(node.get()));
		}
		return result;
	}

	private static <T> Parser scanSuffixed(Parser.Rule<T> child, Parser.ParseDispatch<UnaryOperator<T>> condensers,
			Parser input, Consumer<T> output) {
		final AtomicReference<T> node = new AtomicReference<>();
		Parser result = child.parse(input, node::set).whitespace();
		if (result.isGood()) {
			final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
			final Parser next = result.dispatch(condensers, modifier::set).whitespace();
			if (next.isGood()) {
				result = next;
			} else {
				modifier.set(UnaryOperator.identity());
			}
			output.accept(modifier.get().apply(node.get()));
		}
		return result;
	}

	private final int column;

	private final int line;

	public ExpressionNode(int line, int column) {
		super();
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

	/**
	 * Produce bytecode for this expression
	 */
	public abstract void render(Renderer renderer);

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
	public abstract Imyhat type();

	/**
	 * Perform type checking on this expression and its children.
	 *
	 * @param errorHandler
	 * @return
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);

	/**
	 * Convenience function to produce a type error
	 *
	 * @param acceptable
	 *            the allowed type
	 * @param found
	 *            the type provided
	 */
	protected void typeError(String acceptable, Imyhat found, Consumer<String> errorHandler) {
		errorHandler
				.accept(String.format("%d:%d: Expected %s, but got %s.", line(), column(), acceptable, found.name()));
	}
}
