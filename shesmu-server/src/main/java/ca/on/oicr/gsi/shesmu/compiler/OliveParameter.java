package ca.on.oicr.gsi.shesmu.compiler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A parameter to a “Define” olive
 */
public class OliveParameter extends Target {

	public static Parser parse(Parser parser, Consumer<OliveParameter> output) {
		final AtomicReference<ImyhatNode> type = new AtomicReference<>();
		final AtomicReference<String> name = new AtomicReference<>();
		final Parser result = parser//
				.then(ImyhatNode::parse, type::set)//
				.whitespace()//
				.identifier(name::set)//
				.whitespace();
		if (result.isGood()) {
			output.accept(new OliveParameter(name.get(), type.get()));
		}
		return result;

	}

	private final String name;

	private Imyhat realType;
	private final ImyhatNode type;

	public OliveParameter(String name, ImyhatNode type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public Flavour flavour() {
		return Flavour.PARAMETER;
	}

	@Override
	public String name() {
		return name;
	}

	public boolean resolveTypes(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
		realType = type.render(definedTypes, errorHandler);
		return !realType.isBad();
	}

	@Override
	public Imyhat type() {
		return realType;
	}

}
