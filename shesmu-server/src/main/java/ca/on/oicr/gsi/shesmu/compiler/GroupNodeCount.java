package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A counter grouper in a “Group” clause
 *
 * Also usable as the variable definition for the result
 */
public final class GroupNodeCount extends GroupNode {

	private final String name;

	public GroupNodeCount(int line, int column, String name) {
		super(line, column);
		this.name = name;
	}

	@Override
	public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
		// No free variables
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void render(Regrouper regroup, RootBuilder rootBuilder) {
		regroup.addCount(name());
	}

	@Override
	public boolean resolve(NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Imyhat type() {
		return Imyhat.INTEGER;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return true;
	}
}
