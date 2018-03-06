package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class CollectNodeCount extends CollectNode {

	public CollectNodeCount(int line, int column) {
		super(line, column);
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// No free variables.
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		builder.count();
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Imyhat type() {
		return Imyhat.INTEGER;
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		return true;
	}

}
