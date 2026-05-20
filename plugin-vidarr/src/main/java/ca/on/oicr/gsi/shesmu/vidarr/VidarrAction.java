package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class VidarrAction extends Action {
  protected static final String SORT_KEY_ATTEMPT = "vidarr-attempt";
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

  protected static boolean checkJson(JsonNode json, Pattern query) {
    switch (json.getNodeType()) {
      case ARRAY:
        {
          for (final JsonNode element : json) {
            if (checkJson(element, query)) {
              return true;
            }
          }
          return false;
        }
      case BOOLEAN:
        return query.matcher(Boolean.toString(json.asBoolean())).matches();
      case NUMBER:
        return query.matcher(json.numberValue().toString()).matches();
      case OBJECT:
        {
          final Iterator<Map.Entry<String, JsonNode>> iterator = json.fields();
          while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> field = iterator.next();
            if (query.matcher(field.getKey()).matches() || checkJson(field.getValue(), query)) {
              return true;
            }
          }
          return false;
        }
      case STRING:
        return query.matcher(json.asText()).matches();
      default:
        return false;
    }
  }

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
