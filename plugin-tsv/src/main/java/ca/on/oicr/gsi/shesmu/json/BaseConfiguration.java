package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public abstract class BaseConfiguration {
  private Map<String, JsonNode> defaults = Map.of();
  private boolean missingUsesDefaults;
  private Map<String, Imyhat> types;

  public final Map<String, JsonNode> getDefaults() {
    return defaults;
  }

  public final Map<String, Imyhat> getTypes() {
    return types;
  }

  public final boolean isMissingUsesDefaults() {
    return missingUsesDefaults;
  }

  public final void setDefaults(Map<String, JsonNode> defaults) {
    this.defaults = defaults;
  }

  public final void setMissingUsesDefaults(boolean missingUsesDefaults) {
    this.missingUsesDefaults = missingUsesDefaults;
  }

  public final void setTypes(Map<String, Imyhat> types) {
    this.types = types;
  }
}
