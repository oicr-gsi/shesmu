package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CromwellCall {
  private int attempt;
  private String callRoot;
  private String shardIndex;
  private String stderr;
  private String stdout;

  public int getAttempt() {
    return attempt;
  }

  public String getCallRoot() {
    return callRoot;
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

  public void setCallRoot(String callRoot) {
    this.callRoot = callRoot;
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
