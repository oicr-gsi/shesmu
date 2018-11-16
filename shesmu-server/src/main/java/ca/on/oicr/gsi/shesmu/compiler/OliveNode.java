package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;

/**
 * An olive stanza declaration
 */
public abstract class OliveNode {
	protected enum ClauseStreamOrder {
		BAD, PURE, TRANSFORMED
	}

	private interface OliveConstructor {
		OliveNode create(int line, int column, List<OliveClauseNode> clauses);
	}

	private static final Parser.ParseDispatch<OliveNode> ROOTS = new Parser.ParseDispatch<>();
	private static final Parser.ParseDispatch<OliveConstructor> TERMINAL = new Parser.ParseDispatch<>();

	static {
		TERMINAL.addKeyword("Run", (input, output) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<List<OliveArgumentNode>> arguments = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.keyword("With")//
					.whitespace()//
					.list(arguments::set, OliveArgumentNode::parse, ',');
			if (result.isGood()) {
				output.accept((line, column, clauses) -> new OliveNodeRun(line, column, name.get(), arguments.get(),
						clauses));
			}
			return result;
		});
		TERMINAL.addKeyword("Alert", (input, output) -> {
			final AtomicReference<List<OliveArgumentNode>> labels = new AtomicReference<>();
			final AtomicReference<List<OliveArgumentNode>> annotations = new AtomicReference<>(Collections.emptyList());
			final AtomicReference<ExpressionNode> ttl = new AtomicReference<>();
			Parser result = input//
					.whitespace()//
					.list(labels::set, OliveArgumentNode::parse, ',')//
					.whitespace();
			final Parser annotationsParser = result//
					.keyword("Annotations");
			if (annotationsParser.isGood()) {
				result = annotationsParser//
						.whitespace()//
						.listEmpty(annotations::set, OliveArgumentNode::parse, ',')//
						.whitespace();
			}
			result = result.keyword("For")//
					.whitespace()//
					.then(ExpressionNode::parse, ttl::set);
			if (result.isGood()) {
				output.accept((line, column, clauses) -> new OliveNodeAlert(line, column, labels.get(),
						annotations.get(), ttl.get(), clauses));
			}
			return result;
		});

		ROOTS.addKeyword("Define", (input, output) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<List<OliveParameter>> params = new AtomicReference<>();
			final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol("(")//
					.listEmpty(params::set, OliveParameter::parse, ',')//
					.symbol(")")//
					.whitespace()//
					.list(clauses::set, OliveClauseNode::parse)//
					.whitespace()//
					.symbol(";")//
					.whitespace();
			if (result.isGood()) {
				output.accept(
						new OliveNodeDefinition(input.line(), input.column(), name.get(), params.get(), clauses.get()));
			}
			return result;
		});
		ROOTS.addKeyword("Olive", (input, output) -> {
			final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
			final AtomicReference<OliveConstructor> terminal = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.list(clauses::set, OliveClauseNode::parse)//
					.whitespace()//
					.dispatch(TERMINAL, terminal::set)//
					.symbol(";")//
					.whitespace();
			if (result.isGood()) {
				output.accept(terminal.get().create(input.line(), input.column(), clauses.get()));
			}
			return result;
		});
		ROOTS.addKeyword("Function", (input, output) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<List<OliveParameter>> params = new AtomicReference<>();
			final AtomicReference<ExpressionNode> body = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol("(")//
					.listEmpty(params::set, OliveParameter::parse, ',')//
					.symbol(")")//
					.whitespace()//
					.then(ExpressionNode::parse, body::set)//
					.whitespace()//
					.symbol(";")//
					.whitespace();
			if (result.isGood()) {
				output.accept(
						new OliveNodeFunction(input.line(), input.column(), name.get(), params.get(), body.get()));
			}
			return result;
		});
		ROOTS.addRaw("constant declaration", (input, output) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<ExpressionNode> body = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.symbol("=")//
					.whitespace()//
					.then(ExpressionNode::parse, body::set)//
					.whitespace()//
					.symbol(";")//
					.whitespace();
			if (result.isGood()) {
				output.accept(new OliveNodeConstant(input.line(), input.column(), name.get(), body.get()));
			}
			return result;
		});
	}

	/**
	 * Parse a single olive node stanza
	 */
	public static Parser parse(Parser input, Consumer<OliveNode> output) {
		return input.dispatch(ROOTS, output).whitespace();
	}

	/**
	 * Create {@link OliveDefineBuilder} instances for this olive, if required
	 *
	 * This is part of bytecode generation and happens well after
	 * {@link #collectDefinitions(Map, Map, Consumer)}
	 */
	public abstract void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

	/**
	 * Check the rules that “Call” clauses must only precede “Group” clauses
	 */
	public abstract boolean checkVariableStream(Consumer<String> errorHandler);

	/**
	 * Find all the olive definitions
	 *
	 * This is part of analysis and happens well before
	 * {@link #build(RootBuilder, Map)}
	 */
	public abstract boolean collectDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Map<String, Target> definedConstants, Consumer<String> errorHandler);

	public abstract boolean collectFunctions(Predicate<String> isDefined, Consumer<FunctionDefinition> defineFunctions,
			Consumer<String> errorHandler);

	public abstract Stream<OliveTable> dashboard();

	/**
	 * Generate bytecode for this stanza into the
	 * {@link ActionGenerator#run(Consumer, java.util.function.Supplier)} method
	 */
	public abstract void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

	/**
	 * Resolve all variable definitions
	 */
	public abstract boolean resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, Consumer<String> errorHandler,
			ConstantRetriever constants);

	/**
	 * Resolve all non-variable definitions
	 *
	 * This does the clauses and
	 * {@link #resolveDefinitionsExtra(Map, Function, Function, Consumer)}
	 */
	public abstract boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler);

	public abstract boolean resolveTypes(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler);

	/**
	 * Type check this olive and all its constituent parts
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
