package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.node.ArrayNode;

public class FunctionRequest {
	private ArrayNode args;
	private String name;

	public ArrayNode getArgs() {
		return args;
	}

	public String getName() {
		return name;
	}

	public void setArgs(ArrayNode args) {
		this.args = args;
	}

	public void setName(String name) {
		this.name = name;
	}
}
