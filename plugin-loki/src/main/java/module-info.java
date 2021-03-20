import ca.on.oicr.gsi.shesmu.loki.LokiPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;

module ca.on.oicr.gsi.shesmu.plugin.loki {
  exports ca.on.oicr.gsi.shesmu.loki;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;
  requires java.net.http;

  provides PluginFileType with
      LokiPluginType;
}
