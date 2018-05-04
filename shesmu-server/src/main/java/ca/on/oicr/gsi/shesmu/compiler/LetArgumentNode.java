package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class LetArgumentNode extends Target {
	public static Parser parse(Parser input, Consumer<LetArgumentNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ExpressionNode> expression = new AtomicReference<ExpressionNode>();
		final Parser result = input//
				.whitespace()//
				.identifier(name::set)//
				.whitespace()//
				.symbol("=")//
				.whitespace()//
				.then(ExpressionNode::parse, expression::set)//
				.whitespace();

		if (result.isGood()) {
			output.accept(new LetArgumentNode(name.get(), expression.get()));
		}
		return result;
	}

	private final ExpressionNode expression;

	private final String name;

	public LetArgumentNode(String name, ExpressionNode expression) {
		super();
		this.name = name;
		this.expression = expression;
	}

	public void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);
	}

	@Override
	public Flavour flavour() {
		return Flavour.STREAM;
	}

	@Override
	public String name() {
		return name;
	}

	public void render(LetBuilder let) {
		let.add(expression.type().asmType(), name, expression::render);
	}

	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type();
	}

	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}
}
