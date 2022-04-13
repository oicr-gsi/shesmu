package ca.on.oicr.gsi.shesmu.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public final class LocalConfiguration extends BaseConfiguration {
  private Map<String, Map<String, JsonNode>> values;

  public Map<String, Map<String, JsonNode>> getValues() {
    return values;
  }

  public void setValues(Map<String, Map<String, JsonNode>> values) {
    this.values = values;
  }
}
