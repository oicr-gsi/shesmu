package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ImyhatNodeLiteral extends ImyhatNode {
	private final Imyhat type;

	public ImyhatNodeLiteral(Imyhat type) {
		super();
		this.type = type;
	}

	@Override
	public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		return type;
	}

}
