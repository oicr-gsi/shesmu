package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class VidarrAction extends Action {
  protected final Supplier<VidarrPlugin> owner;
  protected List<String> errors = List.of();
  protected List<String> tags;
  protected final Set<String> services = new TreeSet<>(List.of("vidarr"));
  protected int priority;
  static final Imyhat EXTERNAL_IDS =
      new Imyhat.ObjectImyhat(
              Stream.of(new Pair<>("id", Imyhat.STRING), new Pair<>("provider", Imyhat.STRING)))
          .asList();
  protected boolean stale;

  /**
   * Construct a new action instance
   *
   * @param type the type of the action for use by the front-end
   */
  public VidarrAction(String type, Supplier<VidarrPlugin> owner) {
    super(type);
    this.owner = owner;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("priority", priority);
    services.forEach(node.putArray("services")::add);
    errors.forEach(node.putArray("errors")::add);
    return node;
  }

  @Override
  public int priority() {
    return priority;
  }

  @ActionParameter(required = false)
  public void priority(long priority) {
    this.priority = (int) priority;
  }

  @Override
  public Stream<String> tags() {
    return tags.stream();
  }
}
