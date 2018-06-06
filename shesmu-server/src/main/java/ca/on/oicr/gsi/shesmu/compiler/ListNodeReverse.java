package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeReverse extends ListNode {
	private Imyhat incoming;

	private String name;

	public ListNodeReverse(int line, int column) {
		super(line, column);
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// Do nothing.
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String nextName() {
		return name();
	}

	@Override
	public Imyhat nextType() {
		return incoming;
	}

	@Override
	public Ordering order(Ordering previous, Consumer<String> errorHandler) {
		if (previous == Ordering.RANDOM) {
			errorHandler.accept(
					String.format("%d:%d: The list is not sorted. Reversing it is a bad idea.", line(), column()));
			return Ordering.BAD;
		}
		return previous;
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		builder.reverse();
	}

	@Override
	public Optional<String> resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return Optional.of(name);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		this.incoming = incoming;
		return true;
	}

}
