package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;

public class DiscriminatorNodeRename extends DiscriminatorNode {

	private final ExpressionNode expression;
	private final String name;

	public DiscriminatorNodeRename(int line, int column, String name, ExpressionNode expression) {
		super(line, column);
		this.name = name;
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		expression.collectFreeVariables(names, predicate);
	}

	@Override
	public VariableInformation dashboard() {
		final Set<String> freeStreamVariables = new HashSet<>();
		expression.collectFreeVariables(freeStreamVariables, Flavour::isStream);
		return new VariableInformation(name, expression.type(), freeStreamVariables.stream(), Behaviour.DEFINITION_BY);
	}

	@Override
	public Flavour flavour() {
		return Flavour.STREAM;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void render(RegroupVariablesBuilder builder) {
		builder.addKey(expression.type().asmType(), name, expression::render);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		boolean ok = expression.resolve(defs, errorHandler);
		if (ok) {
			final Set<String> freeStreamVariables = new HashSet<>();
			expression.collectFreeVariables(freeStreamVariables, Flavour::isStream);
			if (freeStreamVariables.isEmpty()) {
				errorHandler.accept(String.format("%d:%d: New variable “%s” in By does not use any stream variables.",
						line(), column(), name));
				ok = false;
			}
		}
		return ok;
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}

}
