package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FakeAction extends JsonParameterisedAction {

  public static final ObjectMapper MAPPER = new ObjectMapper();
  private final String name;
  private final ObjectNode parameters = MAPPER.createObjectNode();

  public FakeAction(String name) {
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
    final FakeAction other = (FakeAction) obj;
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
  public ActionState perform(ActionServices services) {
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
