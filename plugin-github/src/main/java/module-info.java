import ca.on.oicr.gsi.shesmu.github.GitHubBranchesApiPluginType;
import ca.on.oicr.gsi.shesmu.github.GithubBranchesFormatDefinition;
import ca.on.oicr.gsi.shesmu.gitlink.GitSourceLinker;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.github {
  exports ca.on.oicr.gsi.shesmu.github;
  exports ca.on.oicr.gsi.shesmu.gitlink;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;

  provides InputFormat with
      GithubBranchesFormatDefinition;
  provides PluginFileType with
      GitSourceLinker,
      GitHubBranchesApiPluginType;
}
