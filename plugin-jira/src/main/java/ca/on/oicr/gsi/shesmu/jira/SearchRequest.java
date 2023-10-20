package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class SearchRequest {
  private Set<String> fields = Set.of();
  private String jql;
  private int maxResults;
  private int startAt;

  public Set<String> getFields() {
    return fields;
  }

  public String getJql() {
    return jql;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public int getStartAt() {
    return startAt;
  }

  public void setFields(Set<String> fields) {
    this.fields = fields;
  }

  public void setJql(String jql) {
    this.jql = jql;
  }

  public void setMaxResults(int maxResults) {
    this.maxResults = maxResults;
  }

  public void setStartAt(int startAt) {
    this.startAt = startAt;
  }
}
