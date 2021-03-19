package ca.on.oicr.gsi.shesmu.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StaticAction {
  private String name;
  private ObjectNode parameters;
  private List<String> tags = List.of();

  public String getName() {
    return name;
  }

  public ObjectNode getParameters() {
    return parameters;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setParameters(ObjectNode parameters) {
    this.parameters = parameters;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }
}
