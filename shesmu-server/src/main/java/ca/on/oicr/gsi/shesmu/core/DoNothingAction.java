package ca.on.oicr.gsi.shesmu.core;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Pattern;

public class DoNothingAction extends Action {

  @RuntimeInterop public String value = "";

  public DoNothingAction() {
    super("do-nothing");
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
    final DoNothingAction other = (DoNothingAction) obj;
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
  public ActionState perform(ActionServices services) {
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
  public boolean search(Pattern query) {
    return query.matcher(value).matches();
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("value", value);
    return node;
  }
}
