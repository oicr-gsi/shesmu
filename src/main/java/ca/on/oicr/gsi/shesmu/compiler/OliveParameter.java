package ca.on.oicr.gsi.shesmu.compiler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A parameter to a “Define” olive
 */
public class OliveParameter extends Target {

	public static Parser parse(Parser parser, Consumer<OliveParameter> output) {
		final AtomicReference<Imyhat> type = new AtomicReference<>();
		final AtomicReference<String> name = new AtomicReference<>();
		final Parser result = Parser.parseImyhat(parser, type::set).whitespace().identifier(name::set).whitespace();
		if (result.isGood()) {
			output.accept(new OliveParameter(name.get(), type.get()));
		}
		return result;

	}

	private final String name;

	private final Imyhat type;

	public OliveParameter(String name, Imyhat type) {
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

	@Override
	public Imyhat type() {
		return type;
	}

}
