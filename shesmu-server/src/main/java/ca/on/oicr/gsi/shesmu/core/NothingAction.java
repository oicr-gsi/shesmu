package ca.on.oicr.gsi.shesmu.core;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NothingAction extends Action {

  private static final ActionCommand<NothingAction> COMPLAIN =
      new ActionCommand<NothingAction>(
          NothingAction.class,
          "NOTHING-COMPLAIN",
          FrontEndIcon.TERMINAL,
          "Write to Server Console",
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected boolean execute(NothingAction action, Optional<String> user) {
          System.err.println(action.value);
          return true;
        }
      };
  @RuntimeInterop public String value = "";

  public NothingAction() {
    super("nothing");
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return Stream.of(COMPLAIN);
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
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(value.getBytes(StandardCharsets.UTF_8));
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
    return ActionState.ZOMBIE;
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
