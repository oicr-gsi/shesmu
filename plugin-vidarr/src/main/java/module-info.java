import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.vidarr.VidarrAnalysisFormatDefinition;
import ca.on.oicr.gsi.shesmu.vidarr.VidarrPluginType;

module ca.on.oicr.gsi.shesmu.plugin.vidarr {
  exports ca.on.oicr.gsi.shesmu.vidarr;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires ca.on.oicr.gsi.shesmu.plugin.gsi.common;
  requires ca.on.oicr.gsi.vidarr.pluginapi;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires java.net.http;

  provides InputFormat with
      VidarrAnalysisFormatDefinition;
  provides PluginFileType with
      VidarrPluginType;
}
