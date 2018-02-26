package ca.on.oicr.gsi.shesmu.actions.rest;

public class ParameterInfo {
	private boolean required;
	private String type;

	public String getType() {
		return type;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public void setType(String type) {
		this.type = type;
	}

}
