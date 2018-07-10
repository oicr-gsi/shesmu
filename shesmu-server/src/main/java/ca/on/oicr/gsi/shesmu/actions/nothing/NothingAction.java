package ca.on.oicr.gsi.shesmu.actions.nothing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;

public class NothingAction extends Action {

	@RuntimeInterop
	public String value = "";

	public NothingAction() {
		super("nothing");
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
		final NothingAction other = (NothingAction) obj;
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		} else if (!value.equals(other.value)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (value == null ? 0 : value.hashCode());
		return result;
	}

	@Override
	public ActionState perform() {
		return ActionState.SUCCEEDED;
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
		node.put("type", "nothing");
		node.put("value", value);
		return node;
	}

}
