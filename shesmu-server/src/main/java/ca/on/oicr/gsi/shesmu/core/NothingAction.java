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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NothingAction extends Action {

  private static final ActionCommand<NothingAction> COMPLAIN =
      new ActionCommand<>(
          NothingAction.class,
          "NOTHING-COMPLAIN",
          FrontEndIcon.TERMINAL,
          "Write to Server Console",
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected Response execute(NothingAction action, Optional<String> user) {
          System.err.println(action.value);
          return Response.ACCEPTED;
        }
      };
  private static final ActionCommand<NothingAction> SCREAM_TO_DEATH =
      new ActionCommand<>(
          NothingAction.class,
          "NOTHING-SCREAM-TO-DEATH",
          FrontEndIcon.TRASH_FILL,
          "Write to Server Console and Purge",
          100,
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected Response execute(NothingAction action, Optional<String> user) {
          System.err.println(action.value);
          return Response.PURGE;
        }
      };

  @SuppressWarnings("CanBeFinal")
  @RuntimeInterop
  public Map<String, Long> sorting = Map.of();

  @SuppressWarnings("CanBeFinal")
  @RuntimeInterop
  public String value = "";

  public NothingAction() {
    super("nothing");
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return Stream.of(COMPLAIN, SCREAM_TO_DEATH);
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
    final var other = (NothingAction) obj;
    if (value == null) {
      return other.value == null;
    } else return value.equals(other.value);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + (value == null ? 0 : value.hashCode());
    return result;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
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
  public OptionalInt sortKey(String key) {
    return Optional.ofNullable(sorting.get(key))
        .map((Long v) -> OptionalInt.of(v.intValue()))
        .orElse(OptionalInt.empty());
  }

  @Override
  public Stream<String> sortKeys() {
    return sorting.keySet().stream();
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("value", value);
    return node;
  }
}
