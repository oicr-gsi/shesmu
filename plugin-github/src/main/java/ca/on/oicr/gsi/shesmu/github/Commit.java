package ca.on.oicr.gsi.shesmu.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Commit {
  private String sha;
  private String url;

  public String getSha() {
    return sha;
  }

  public String getUrl() {
    return url;
  }

  public void setSha(String sha) {
    this.sha = sha;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
