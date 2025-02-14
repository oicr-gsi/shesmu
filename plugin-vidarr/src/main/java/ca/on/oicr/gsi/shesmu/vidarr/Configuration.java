package ca.on.oicr.gsi.shesmu.vidarr;

import java.util.List;

public class Configuration {
  private Long defaultMaxSubmissionDelay;
  private String url;
  private List<String> analysisTypes;
  private List<String> versionTypes;

  public Long getDefaultMaxSubmissionDelay() {
    return defaultMaxSubmissionDelay;
  }

  public String getUrl() {
    return url;
  }

  public List<String> getAnalysisTypes() {
    return analysisTypes;
  }

  public List<String> getVersionTypes() {
    return versionTypes;
  }

  public void setDefaultMaxSubmissionDelay(Long defaultMaxSubmissionDelay) {
    this.defaultMaxSubmissionDelay = defaultMaxSubmissionDelay;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setAnalysisTypes(List<String> analysisTypes) {
    this.analysisTypes = analysisTypes;
  }

  public void setVersionTypes(List<String> versionTypes) {
    this.versionTypes = versionTypes;
  }
}
