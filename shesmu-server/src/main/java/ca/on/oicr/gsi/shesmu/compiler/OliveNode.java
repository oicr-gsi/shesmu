package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

/**
 * An olive stanza declaration
 */
public abstract class OliveNode {
	protected enum ClauseStreamOrder {
		BAD, PURE, TRANSFORMED
	}

	private static final Parser.ParseDispatch<OliveNode> ROOTS = new Parser.ParseDispatch<>();

	static {
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
		ROOTS.addKeyword("Run", (input, output) -> {
			final AtomicReference<String> name = new AtomicReference<>();
			final AtomicReference<List<OliveArgumentNode>> arguments = new AtomicReference<>();
			final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
			final Parser result = input//
					.whitespace()//
					.identifier(name::set)//
					.whitespace()//
					.list(clauses::set, OliveClauseNode::parse)//
					.whitespace()//
					.keyword("With")//
					.whitespace()//
					.symbol("{")//
					.whitespace()//
					.list(arguments::set, OliveArgumentNode::parse, ',')//
					.symbol("}")//
					.whitespace();
			if (result.isGood()) {
				output.accept(
						new OliveNodeRun(input.line(), input.column(), name.get(), arguments.get(), clauses.get()));
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

	private final List<OliveClauseNode> clauses;
	protected final Set<String> signableNames = new TreeSet<>();

	public OliveNode(List<OliveClauseNode> clauses) {
		super();
		this.clauses = clauses;
	}

	/**
	 * Create {@link OliveDefineBuilder} instances for this olive, if required
	 *
	 * This is part of bytecode generation and happens well after
	 * {@link #collectDefinitions(Map, Consumer)}
	 */
	protected abstract void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

	/**
	 * Check the rules that “Matches” clauses must only precede “Group” clauses
	 */
	public final boolean checkVariableStream(Consumer<String> errorHandler) {
		ClauseStreamOrder state = ClauseStreamOrder.PURE;
		for (final OliveClauseNode clause : clauses()) {
			state = clause.ensureRoot(state, signableNames, errorHandler);
		}
		if (state == ClauseStreamOrder.PURE) {
			collectArgumentSignableVariables();
		}
		return state != ClauseStreamOrder.BAD;
	}

	/**
	 * List all the clauses in this node
	 */
	protected List<OliveClauseNode> clauses() {
		return clauses;
	}

	protected abstract void collectArgumentSignableVariables();

	/**
	 * Find all the olive definitions
	 *
	 * This is part of analysis and happens well before
	 * {@link #build(RootBuilder, Map)}
	 */
	protected abstract boolean collectDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Consumer<String> errorHandler);

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
			Supplier<Stream<Constant>> constants);

	/**
	 * Resolve all non-variable definitions
	 *
	 * This does the clauses and
	 * {@link #resolveDefinitionsExtra(Map, Function, Function, Consumer)}
	 */
	public final boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		final boolean clausesOk = clauses.stream().filter(clause -> clause.resolveDefinitions(definedOlives,
				definedFunctions, definedActions, metricNames, dumpers, errorHandler)).count() == clauses.size();
		return clausesOk & resolveDefinitionsExtra(definedOlives, definedFunctions, definedActions, errorHandler);
	}

	/**
	 * Do any further non-variable definition resolution specific to this class
	 */
	protected abstract boolean resolveDefinitionsExtra(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler);

	/**
	 * Type check this olive and all its constituent parts
	 */
	public final boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = clauses.stream().filter(clause -> clause.typeCheck(errorHandler)).count() == clauses.size();
		return ok & typeCheckExtra(errorHandler);
	}

	protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
