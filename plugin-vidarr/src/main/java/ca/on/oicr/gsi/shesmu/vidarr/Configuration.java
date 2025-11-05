package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.vidarr.api.AnalysisOutputType;
import java.util.List;

public class Configuration {
  private boolean canSubmit = true;
  private Long defaultMaxSubmissionDelay;
  private String url;
  private List<AnalysisOutputType> analysisTypes;
  private List<String> versionTypes;
  private int timeout;

  public boolean isCanSubmit() {
    return canSubmit;
  }

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

  public void setCanSubmit(boolean canSubmit) {
    this.canSubmit = canSubmit;
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

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
