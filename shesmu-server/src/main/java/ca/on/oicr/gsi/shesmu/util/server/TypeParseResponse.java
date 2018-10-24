package ca.on.oicr.gsi.shesmu.util.server;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class TypeParseResponse {
	private String descriptor;
	private String humanName;

	public TypeParseResponse(Imyhat input) {
		humanName = input.name();
		descriptor = input.signature();
	}

	public String getDescriptor() {
		return descriptor;
	}

	public String getHumanName() {
		return humanName;
	}

	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	public void setHumanName(String humanName) {
		this.humanName = humanName;
	}
}
