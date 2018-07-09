package ca.on.oicr.gsi.shesmu;

public final class FunctionParameter {

	private final String name;

	private final Imyhat type;

	public FunctionParameter(String name, Imyhat type) {
		super();
		this.name = name;
		this.type = type;
	}

	public String name() {
		return name;
	}

	public Imyhat type() {
		return type;
	}

}
