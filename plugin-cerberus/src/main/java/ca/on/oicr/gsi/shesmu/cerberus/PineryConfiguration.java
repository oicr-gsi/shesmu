package ca.on.oicr.gsi.shesmu.cerberus;

import java.util.Set;

public final class PineryConfiguration {

  private String url;
  private Set<Integer> versions;

  public String getUrl() {
    return url;
  }

  public Set<Integer> getVersions() {
    return versions;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setVersions(Set<Integer> versions) {
    this.versions = versions;
  }
}
