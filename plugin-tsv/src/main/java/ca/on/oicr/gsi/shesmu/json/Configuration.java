package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public class Configuration {
  private Map<String, JsonNode> defaults = Map.of();
  private boolean missingUsesDefaults;
  private Map<String, Imyhat> types;
  private Map<String, Map<String, JsonNode>> values;

  public Map<String, JsonNode> getDefaults() {
    return defaults;
  }

  public Map<String, Imyhat> getTypes() {
    return types;
  }

  public Map<String, Map<String, JsonNode>> getValues() {
    return values;
  }

  public boolean isMissingUsesDefaults() {
    return missingUsesDefaults;
  }

  public void setDefaults(Map<String, JsonNode> defaults) {
    this.defaults = defaults;
  }

  public void setMissingUsesDefaults(boolean missingUsesDefaults) {
    this.missingUsesDefaults = missingUsesDefaults;
  }

  public void setTypes(Map<String, Imyhat> types) {
    this.types = types;
  }

  public void setValues(Map<String, Map<String, JsonNode>> values) {
    this.values = values;
  }
}
