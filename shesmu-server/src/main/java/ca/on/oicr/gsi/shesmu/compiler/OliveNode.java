package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.Lookup;

/**
 * An olive stanza declaration
 */
public abstract class OliveNode {
	protected enum ClauseStreamOrder {
		BAD, GROUPED, PURE
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
		return input.dispatch(ROOTS, output);
	}

	/**
	 * Parse a file of olive nodes
	 */
	public static boolean parseFile(CharSequence input, Consumer<List<OliveNode>> output, ErrorConsumer errorHandler) {
		final Parser result = Parser.start(input, errorHandler).whitespace().list(output, OliveNode::parse)
				.whitespace();
		if (result.isGood()) {
			if (result.isEmpty()) {
				return true;
			} else {
				errorHandler.raise(result.line(), result.column(), "Junk at end of file.");
			}
		}
		return false;
	}

	/**
	 * Generate bytecode for this definition
	 */
	public static void render(RootBuilder builder, List<OliveNode> program) {
		final Map<String, OliveDefineBuilder> definitions = new HashMap<>();
		program.forEach(olive -> olive.build(builder, definitions));
		program.forEach(olive -> olive.render(builder, definitions));
	}

	/**
	 * Check that a collection of olives, assumed to be a self-contained program, is
	 * well-formed.
	 *
	 * @param olives
	 *            the olives that make up the program
	 * @param definedLookups
	 *            the lookups available; if a lookup is not found, null should be
	 *            returned
	 * @param definedActions
	 *            the actions available; if an action is not found, null should be
	 *            returned
	 * @param constants
	 */
	public static boolean validate(List<OliveNode> olives, Function<String, Lookup> definedLookups,
			Function<String, ActionDefinition> definedActions, Consumer<String> errorHandler,
			Supplier<Stream<Constant>> constants) {

		// Find and resolve olive “Define” and “Matches”
		final Map<String, OliveNodeDefinition> definedOlives = new HashMap<>();
		final Set<String> metricNames = new HashSet<>();
		boolean ok = olives.stream().filter(olive -> olive.collectDefinitions(definedOlives, errorHandler))
				.count() == olives.size();
		ok &= olives.stream().filter(olive -> olive.resolveDefinitions(definedOlives, definedLookups, definedActions,
				metricNames, errorHandler)).count() == olives.size();

		// Resolve variables
		if (ok) {
			ok = olives.stream().filter(olive -> olive.resolve(errorHandler, constants)).count() == olives.size();
		}

		// Type check the resolved structure
		if (ok) {
			ok = olives.stream().filter(olive -> olive.typeCheck(errorHandler)).count() == olives.size();
		}
		return ok;
	}

	private final List<OliveClauseNode> clauses;

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
			state = clause.ensureRoot(state, errorHandler);
		}
		return state != ClauseStreamOrder.BAD;
	}

	/**
	 * List all the clauses in this node
	 */
	protected List<OliveClauseNode> clauses() {
		return clauses;
	}

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
	 *
	 * @param constants
	 */
	public abstract boolean resolve(Consumer<String> errorHandler, Supplier<Stream<Constant>> constants);

	/**
	 * Resolve all non-variable definitions
	 *
	 * This does the clauses and
	 * {@link #resolveDefinitionsExtra(Map, Function, Function, Consumer)}
	 */
	public final boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		final boolean clausesOk = clauses.stream().filter(clause -> clause.resolveDefinitions(definedOlives,
				definedLookups, definedActions, metricNames, errorHandler)).count() == clauses.size();
		return clausesOk & resolveDefinitionsExtra(definedOlives, definedLookups, definedActions, errorHandler);
	}

	/**
	 * Do any further non-variable definition resolution specific to this class
	 */
	protected abstract boolean resolveDefinitionsExtra(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
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
