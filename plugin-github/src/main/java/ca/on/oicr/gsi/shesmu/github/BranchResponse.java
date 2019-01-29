package ca.on.oicr.gsi.shesmu.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchResponse {
  private Commit commit;
  private String name;

  public Commit getCommit() {
    return commit;
  }

  public String getName() {
    return name;
  }

  public void setCommit(Commit commit) {
    this.commit = commit;
  }

  public void setName(String name) {
    this.name = name;
  }
}
