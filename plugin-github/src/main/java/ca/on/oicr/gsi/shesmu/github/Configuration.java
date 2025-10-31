package ca.on.oicr.gsi.shesmu.github;

public class Configuration {
  private String owner;
  private String repo;
  private int timeout;

  public String getOwner() {
    return owner;
  }

  public String getRepo() {
    return repo;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public void setRepo(String repo) {
    this.repo = repo;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
