package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class SearchResponse {
  private List<Issue> issues = List.of();
  private int total;

  public List<Issue> getIssues() {
    return issues;
  }

  public int getTotal() {
    return total;
  }

  public void setIssues(List<Issue> issues) {
    this.issues = issues;
  }

  public void setTotal(int total) {
    this.total = total;
  }
}
