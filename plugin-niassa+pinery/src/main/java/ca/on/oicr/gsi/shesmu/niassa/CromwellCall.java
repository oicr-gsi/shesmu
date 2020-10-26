package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CromwellCall {
  private int attempt;
  private String backend;
  private String callRoot;
  private String executionStatus;
  private List<CromwellFailure> failures = Collections.emptyList();
  private String jobId;
  private String shardIndex;
  private String stderr;
  private String stdout;

  public int getAttempt() {
    return attempt;
  }

  public String getBackend() {
    return backend;
  }

  public String getCallRoot() {
    return callRoot;
  }

  public String getExecutionStatus() {
    return executionStatus;
  }

  public List<CromwellFailure> getFailures() {
    return failures;
  }

  public String getJobId() {
    return jobId;
  }

  public String getShardIndex() {
    return shardIndex;
  }

  public String getStderr() {
    return stderr;
  }

  public String getStdout() {
    return stdout;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public void setBackend(String backend) {
    this.backend = backend;
  }

  public void setCallRoot(String callRoot) {
    this.callRoot = callRoot;
  }

  public void setExecutionStatus(String executionStatus) {
    this.executionStatus = executionStatus;
  }

  public void setFailures(List<CromwellFailure> failures) {
    this.failures = failures;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public void setShardIndex(String shardIndex) {
    this.shardIndex = shardIndex;
  }

  public void setStderr(String stderr) {
    this.stderr = stderr;
  }

  public void setStdout(String stdout) {
    this.stdout = stdout;
  }
}
