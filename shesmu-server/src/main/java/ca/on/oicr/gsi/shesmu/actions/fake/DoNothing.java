package ca.on.oicr.gsi.shesmu.actions.fake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameterised;

public class DoNothing extends Action implements JsonParameterised {

	private final String name;
	private final ObjectNode parameters = RuntimeSupport.MAPPER.createObjectNode();

	public DoNothing(String name) {
		super("fake");
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final DoNothing other = (DoNothing) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!parameters.equals(other.parameters)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (name == null ? 0 : name.hashCode());
		result = prime * result + (parameters == null ? 0 : parameters.hashCode());
		return result;
	}

	@Override
	public ObjectNode parameters() {
		return parameters;
	}

	@Override
	public ActionState perform() {
		return ActionState.UNKNOWN;
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public long retryMinutes() {
		return 10;
	}

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("name", name);
		node.set("parameters", parameters);
		return node;
	}

}
