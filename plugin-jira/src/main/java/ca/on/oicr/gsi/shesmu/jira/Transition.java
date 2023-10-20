package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Transition {
  private Map<String, IssueField> fields;
  private String id;
  private String name;
  private Status to;

  public Map<String, IssueField> getFields() {
    return fields;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Status getTo() {
    return to;
  }

  public void setFields(Map<String, IssueField> fields) {
    this.fields = fields;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTo(Status to) {
    this.to = to;
  }
}
