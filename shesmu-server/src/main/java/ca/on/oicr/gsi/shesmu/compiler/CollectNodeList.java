package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class CollectNodeList extends CollectNode {

	private final ExpressionNode expression;
	private Imyhat incomingType;
	private String name;
	private final Target parameter = new Target() {

		@Override
		public Flavour flavour() {
			return Flavour.LAMBDA;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Imyhat type() {
			return incomingType;
		}
	};

	public CollectNodeList(int line, int column, ExpressionNode expression) {
		super(line, column);
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		final boolean remove = !names.contains(name);
		expression.collectFreeVariables(names);
		if (remove) {
			names.remove(name);
		}
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		expression.collectFreeVariables(freeVariables);
		Renderer renderer = builder.map(name, expression.type(), builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		expression.render(renderer);
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
		builder.collect();
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return expression.resolve(defs.bind(parameter), errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type().asList();
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		incomingType = incoming;
		return expression.typeCheck(errorHandler);
	}

}
