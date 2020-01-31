package ca.on.oicr.gsi.shesmu.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;

public class Configuration {
  private Map<String, JsonNode> defaults = Collections.emptyMap();
  private Map<String, String> types;
  private Map<String, Map<String, JsonNode>> values;

  public Map<String, JsonNode> getDefaults() {
    return defaults;
  }

  public Map<String, String> getTypes() {
    return types;
  }

  public Map<String, Map<String, JsonNode>> getValues() {
    return values;
  }

  public void setDefaults(Map<String, JsonNode> defaults) {
    this.defaults = defaults;
  }

  public void setTypes(Map<String, String> types) {
    this.types = types;
  }

  public void setValues(Map<String, Map<String, JsonNode>> values) {
    this.values = values;
  }
}
