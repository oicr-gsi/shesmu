package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.authentication.AuthenticationConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public final class Configuration {
  private AuthenticationConfiguration authentication;
  private List<String> closedStatuses;
  private Map<String, JsonNode> defaultFieldValues = Map.of();
  private String issueType;
  private String projectKey;
  private List<Search> searches = List.of();
  private String url;
  private String user;
  private JiraVersion version = JiraVersion.V2;

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public List<String> getClosedStatuses() {
    return closedStatuses;
  }

  public Map<String, JsonNode> getDefaultFieldValues() {
    return defaultFieldValues;
  }

  public String getIssueType() {
    return issueType;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public List<Search> getSearches() {
    return searches;
  }

  public String getUrl() {
    return url;
  }

  public String getUser() {
    return user;
  }

  public JiraVersion getVersion() {
    return version;
  }

  public void setAuthentication(AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  public void setClosedStatuses(List<String> closedStatuses) {
    this.closedStatuses = closedStatuses;
  }

  public void setDefaultFieldValues(Map<String, JsonNode> defaultFieldValues) {
    this.defaultFieldValues = defaultFieldValues;
  }

  public void setIssueType(String issueType) {
    this.issueType = issueType;
  }

  public void setProjectKey(String projectKey) {
    this.projectKey = projectKey;
  }

  public void setSearches(List<Search> searches) {
    this.searches = searches;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public void setVersion(JiraVersion version) {
    this.version = version;
  }
}
