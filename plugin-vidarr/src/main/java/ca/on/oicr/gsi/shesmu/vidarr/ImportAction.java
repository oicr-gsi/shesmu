package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.ImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportAction extends Action {
  private boolean stale;
  private int priority;
  final ImportRequest request = new ImportRequest();
  final Supplier<VidarrPlugin> owner;
  private final Set<String> services = new TreeSet<>(List.of("vidarr"));
  RunState state = new RunStateAttemptSubmit();
  // private final List<String> tags;

  public ImportAction(Supplier<VidarrPlugin> owner) {
    super("vidarr-import");
    this.owner = owner;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (null == other || getClass() != other.getClass()) return false;
    ImportAction o = (ImportAction) other;
    return stale == o.stale;
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return state.commands().commands();
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {}

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    return null;
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 0;
  }

  @Override
  public boolean search(Pattern query) {
    return false;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    return null;
  }
}
