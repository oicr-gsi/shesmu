import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.vidarr.VidarrPluginType;

module ca.on.oicr.gsi.shesmu.plugin.vidarr {
  exports ca.on.oicr.gsi.shesmu.vidarr;

  requires ca.on.oicr.gsi.vidarr.pluginapi;
  requires ca.on.oicr.gsi.shesmu;
  requires ca.on.oicr.gsi.serverutils;
  requires java.net.http;
  requires com.fasterxml.jackson.datatype.jsr310;

  provides PluginFileType with
      VidarrPluginType;
}
