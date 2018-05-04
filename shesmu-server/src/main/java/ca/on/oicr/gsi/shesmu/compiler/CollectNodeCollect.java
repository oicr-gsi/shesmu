package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class CollectNodeCollect extends CollectNode {

	private Imyhat type;

	public CollectNodeCollect(int line, int column) {
		super(line, column);
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// No free variables.
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		builder.collect();
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Imyhat type() {
		return type;
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		type = incoming.asList();
		return true;
	}

}
