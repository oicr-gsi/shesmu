package ca.on.oicr.gsi.shesmu.plugin.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Pattern;

/** An {@link Action} that stores some of its parameters in a JSON object */
public abstract class JsonParameterisedAction extends Action {
  public JsonParameterisedAction(String type) {
    super(type);
  }

  /**
   * The JSON object to mutate when writing parameters.
   *
   * <p>This must not return null.
   */
  public abstract ObjectNode parameters();

  /**
   * Check if the JSON object returned by {@link #parameters()} contains any strings that match the
   * pattern supplied
   */
  protected final boolean searchParameters(Pattern pattern) {
    for (var value : parameters()) {
      if (searchParameters(pattern, value)) return true;
    }
    return false;
  }

  @Override
  public boolean search(Pattern query) {
    return searchParameters(query);
  }

  /** Check if the JSON value provided contains any strings that match the pattern supplied */
  protected final boolean searchParameters(Pattern pattern, JsonNode value) {
    if (value.isTextual()) {
      return pattern.matcher(value.asText()).matches();
    }
    for (var inner : value) {
      if (searchParameters(pattern, inner)) return true;
    }
    return false;
  }
}
