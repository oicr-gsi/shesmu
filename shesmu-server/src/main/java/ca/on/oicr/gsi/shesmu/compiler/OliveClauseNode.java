package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

/**
 * Base type for an olive clause
 */
public abstract class OliveClauseNode {
	private static final Pattern HELP = Pattern.compile("^\"([^\"]*)\"");
	private static final Pattern OPTIMA = Pattern.compile("^(Min|Max)");

	public static Parser parse(Parser input, Consumer<OliveClauseNode> output) {
		input = input.whitespace();
		Parser inner = parseMonitor(input, output);
		if (inner != null) {
			return inner;
		}
		inner = parseDump(input, output);
		if (inner != null) {
			return inner;
		}
		final Parser whereParser = input.keyword("Where");
		if (whereParser.isGood()) {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = ExpressionNode.parse(whereParser.whitespace(), expression::set).whitespace();
			if (result.isGood()) {
				output.accept(new OliveClauseNodeWhere(input.line(), input.column(), expression.get()));
			}
			return result;
		}
		final Parser groupParser = input.keyword("Group");
		if (groupParser.isGood()) {
			final AtomicReference<List<GroupNode>> groups = new AtomicReference<>();
			final AtomicReference<List<String>> discriminators = new AtomicReference<>();
			final Parser result = groupParser//
					.whitespace()//
					.listEmpty(groups::set, GroupNode::parse, ',')//
					.whitespace()//
					.keyword("By")//
					.whitespace()//
					.list(discriminators::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')//
					.whitespace();
			if (result.isGood()) {
				output.accept(
						new OliveClauseNodeGroup(input.line(), input.column(), groups.get(), discriminators.get()));
			}
			return result;
		}
		final Parser leftJoinParser = input.keyword("LeftJoin");
		if (leftJoinParser.isGood()) {
			final AtomicReference<List<GroupNode>> groups = new AtomicReference<>();
			final AtomicReference<String> format = new AtomicReference<>();
			final Parser result = leftJoinParser//
					.whitespace()//
					.identifier(format::set)//
					.whitespace()//
					.list(groups::set, GroupNode::parse, ',')//
					.whitespace();
			if (result.isGood()) {
				output.accept(new OliveClauseNodeLeftJoin(input.line(), input.column(), format.get(), groups.get()));
			}
			return result;
		}
		final Parser letParser = input.keyword("Let");
		if (letParser.isGood()) {
			final AtomicReference<List<LetArgumentNode>> arguments = new AtomicReference<>();
			final Parser result = letParser//
					.whitespace()//
					.listEmpty(arguments::set, LetArgumentNode::parse, ',')//
					.whitespace();

			if (result.isGood()) {
				output.accept(new OliveClauseNodeLet(input.line(), input.column(), arguments.get()));
			}
			return result;
		}
		final Parser rejectParser = input.keyword("Reject");
		if (rejectParser.isGood()) {
			final AtomicReference<List<RejectNode>> handlers = new AtomicReference<>();
			final AtomicReference<ExpressionNode> clause = new AtomicReference<>();
			final Parser result = rejectParser//
					.whitespace()//
					.then(ExpressionNode::parse, clause::set)//
					.whitespace()//
					.symbol("{")//
					.whitespace()//
					.listEmpty(handlers::set, OliveClauseNode::parseReject, ',')//
					.whitespace()//
					.symbol("}")//
					.whitespace();

			if (result.isGood()) {
				output.accept(new OliveClauseNodeReject(input.line(), input.column(), clause.get(), handlers.get()));
			}
			return result;
		}
		final Parser joinParser = input.keyword("Join");
		if (joinParser.isGood()) {
			final AtomicReference<String> format = new AtomicReference<>();
			final Parser result = joinParser//
					.whitespace()//
					.identifier(format::set)//
					.whitespace();

			if (result.isGood()) {
				output.accept(new OliveClauseNodeJoin(input.line(), input.column(), format.get()));
			}
			return result;
		}
		final Parser pickParser = input.keyword("Pick");
		if (pickParser.isGood()) {
			final AtomicReference<Boolean> direction = new AtomicReference<>();
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final AtomicReference<List<String>> discriminators = new AtomicReference<>();
			final Parser result = pickParser//
					.whitespace()//
					.regex(OPTIMA, m -> direction.set(m.group().equals("Max")), "Need Min or Max.")//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set)//
					.whitespace()//
					.keyword("By")//
					.whitespace()//
					.list(discriminators::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')//
					.whitespace();
			if (result.isGood()) {
				output.accept(new OliveClauseNodePick(input.line(), input.column(), direction.get(), expression.get(),
						discriminators.get()));
			}
			return result;
		}
		final AtomicReference<String> name = new AtomicReference<>();
		final Parser callParser = input.identifier(name::set);
		if (callParser.isGood()) {
			final AtomicReference<List<ExpressionNode>> arguments = new AtomicReference<>();
			final Parser result = callParser//
					.whitespace()//
					.symbol("(")//
					.whitespace()//
					.listEmpty(arguments::set, ExpressionNode::parse, ',')//
					.whitespace()//
					.symbol(")")//
					.whitespace();
			if (result.isGood()) {
				output.accept(new OliveClauseNodeCall(input.line(), input.column(), name.get(), arguments.get()));
			}
			return result;
		}
		return input.raise("Expected olive clause.");
	}

	private static Parser parseDump(Parser input, Consumer<? super OliveClauseNodeDump> output) {
		final Parser dumpParser = input.keyword("Dump");
		if (dumpParser.isGood()) {
			final AtomicReference<List<ExpressionNode>> columns = new AtomicReference<>();
			final AtomicReference<String> dumper = new AtomicReference<>();
			final Parser result = dumpParser//
					.whitespace()//
					.listEmpty(columns::set, ExpressionNode::parse, ',')//
					.whitespace()//
					.keyword("To")//
					.whitespace()//
					.identifier(dumper::set)//
					.whitespace();

			if (result.isGood()) {
				output.accept(new OliveClauseNodeDump(input.line(), input.column(), dumper.get(), columns.get()));
			}
			return result;
		}
		return null;
	}

	private static Parser parseMonitor(Parser input, Consumer<? super OliveClauseNodeMonitor> output) {
		final Parser monitorParser = input.keyword("Monitor");
		if (monitorParser.isGood()) {
			final AtomicReference<String> metricName = new AtomicReference<>();
			final AtomicReference<String> help = new AtomicReference<>();
			final AtomicReference<List<MonitorArgumentNode>> labels = new AtomicReference<>();

			final Parser result = monitorParser//
					.whitespace()//
					.identifier(metricName::set)//
					.whitespace()//
					.regex(HELP, m -> help.set(m.group(1)), "Failed to parse help text")//
					.whitespace()//
					.symbol("{")//
					.listEmpty(labels::set, MonitorArgumentNode::parse, ',')//
					.symbol("}")//
					.whitespace();

			if (result.isGood()) {
				output.accept(new OliveClauseNodeMonitor(input.line(), input.column(), metricName.get(), help.get(),
						labels.get()));
			}
			return result;
		}
		return null;
	}

	public static Parser parseReject(Parser input, Consumer<RejectNode> output) {
		Parser inner = parseMonitor(input, output);
		if (inner != null) {
			return inner;
		}
		inner = parseDump(input, output);
		if (inner != null) {
			return inner;
		}
		return input.raise("Expected olive clause.");
	}

	/**
	 * Check whether the variable stream is acceptable to the clause
	 *
	 * @param state
	 *            the current variable state
	 */
	public abstract ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler);

	/**
	 * Generate byte code for this clause.
	 *
	 * This will consume a stream off the stack, manipulate it as necessary, and
	 * leave a new stream on the stack. Any required other classes or methods must
	 * be generated by the clause.
	 */
	public abstract void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions);

	/**
	 * Resolve all variable definitions in this clause
	 *
	 * @param inputFormatDefinition
	 *            the input format for this olive
	 * @param definedFormats
	 *            the function to find input formats by name
	 * @param defs
	 *            the variable definitions available to this clause
	 * @return the variable definitions available to the next clause
	 */
	public abstract NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler);

	/**
	 * Resolve all non-variable definitions
	 */
	public abstract boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler);

	/**
	 * Type any expression in the clause
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);

}
