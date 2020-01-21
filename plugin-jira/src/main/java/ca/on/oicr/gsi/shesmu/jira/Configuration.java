package ca.on.oicr.gsi.shesmu.jira;

import java.util.Collections;
import java.util.List;

public final class Configuration {
  private List<String> closeActions;
  private List<String> closedStatuses;
  private String passwordFile;
  private String projectKey;
  private List<String> reopenActions;
  private List<Search> searches = Collections.emptyList();
  private String url;
  private String user;

  public List<String> getCloseActions() {
    return closeActions;
  }

  public List<String> getClosedStatuses() {
    return closedStatuses;
  }

  public String getPasswordFile() {
    return passwordFile;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public List<String> getReopenActions() {
    return reopenActions;
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

  public void setCloseActions(List<String> closeActions) {
    this.closeActions = closeActions;
  }

  public void setClosedStatuses(List<String> closedStatuses) {
    this.closedStatuses = closedStatuses;
  }

  public void setPasswordFile(String passwordFile) {
    this.passwordFile = passwordFile;
  }

  public void setProjectKey(String projectKey) {
    this.projectKey = projectKey;
  }

  public void setReopenActions(List<String> reopenActions) {
    this.reopenActions = reopenActions;
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
}
