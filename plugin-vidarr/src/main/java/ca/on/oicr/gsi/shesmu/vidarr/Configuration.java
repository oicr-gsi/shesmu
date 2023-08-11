package ca.on.oicr.gsi.shesmu.vidarr;

public class Configuration {
  private Long defaultMaxSubmissionDelay;
  private String url;

  public Long getDefaultMaxSubmissionDelay() {
    return defaultMaxSubmissionDelay;
  }

  public String getUrl() {
    return url;
  }

  public void setDefaultMaxSubmissionDelay(Long defaultMaxSubmissionDelay) {
    this.defaultMaxSubmissionDelay = defaultMaxSubmissionDelay;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
