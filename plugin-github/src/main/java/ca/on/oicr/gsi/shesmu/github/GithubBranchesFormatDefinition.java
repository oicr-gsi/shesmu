package ca.on.oicr.gsi.shesmu.github;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class GithubBranchesFormatDefinition extends InputFormat {

  public GithubBranchesFormatDefinition() {
    super("github_branches", GithubBranchValue.class);
  }
}
