import ca.on.oicr.gsi.shesmu.jira.JiraPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;

module ca.on.oicr.gsi.shesmu.plugin.jira {
  exports ca.on.oicr.gsi.shesmu.jira;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires simpleclient;

  provides PluginFileType with
      JiraPluginType;
}
