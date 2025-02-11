package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.vidarr.api.AnalysisOutputType;
import java.util.List;

public class Configuration {
  private Long defaultMaxSubmissionDelay;
  private String url;
  private List<AnalysisOutputType> analysisTypes;
  private List<String> versionTypes;

  public Long getDefaultMaxSubmissionDelay() {
    return defaultMaxSubmissionDelay;
  }

  public String getUrl() {
    return url;
  }

  public List<AnalysisOutputType> getAnalysisTypes() {
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

  public void setAnalysisTypes(List<AnalysisOutputType> analysisTypes) {
    this.analysisTypes = analysisTypes;
  }

  public void setVersionTypes(List<String> versionTypes) {
    this.versionTypes = versionTypes;
  }
}
