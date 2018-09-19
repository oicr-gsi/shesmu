package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ImyhatNodeVariable extends ImyhatNode {
	private final String name;

	public ImyhatNodeVariable(String name) {
		super();
		this.name = name;
	}

	@Override
	public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		final Imyhat result = definedTypes.apply(name);
		if (result == null) {
			errorHandler.accept(String.format("Unknown type “%s“.", name));
			return Imyhat.BAD;
		}
		return result;
	}

}
