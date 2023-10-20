package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Project {
  private String id;
  private List<IssueType> issueTypes;

  public String getId() {
    return id;
  }

  public List<IssueType> getIssueTypes() {
    return issueTypes;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setIssueTypes(List<IssueType> issueTypes) {
    this.issueTypes = issueTypes;
  }
}
