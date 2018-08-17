package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;

/**
 * A collection action in a “Group” clause
 *
 * Also usable as the variable definition for the result
 */
public abstract class ByChildNode extends Target {

	private final int column;
	private final int line;
	private final String name;

	public ByChildNode(int line, int column, String name) {
		this.line = line;
		this.column = column;
		this.name = name;
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

	@Override
	public final String name() {
		return name;
	}

	public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

	public abstract boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler);

	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
