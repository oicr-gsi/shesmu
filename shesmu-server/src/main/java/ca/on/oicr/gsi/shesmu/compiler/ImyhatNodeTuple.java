package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class ImyhatNodeTuple extends ImyhatNode {

	private final List<ImyhatNode> types;

	public ImyhatNodeTuple(List<ImyhatNode> types) {
		this.types = types;
	}

	@Override
	public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		return Imyhat.tuple(types.stream().map(t -> t.render(definedTypes, errorHandler)).toArray(Imyhat[]::new));
	}

}
