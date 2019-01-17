package ca.on.oicr.gsi.shesmu.core.linker;

public class GitConfiguration {
  private String prefix;
  private RepoType type;
  private String url;

  public String getPrefix() {
    return prefix;
  }

  public RepoType getType() {
    return type;
  }

  public String getUrl() {
    return url;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setType(RepoType type) {
    this.type = type;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
