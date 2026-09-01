package ca.on.oicr.gsi.shesmu.json;

import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class LocalConfiguration extends BaseConfiguration {
  private Map<String, Map<String, JsonNode>> values;

  public Map<String, Map<String, JsonNode>> getValues() {
    return values;
  }

  public void setValues(Map<String, Map<String, JsonNode>> values) {
    this.values = values;
  }
}
