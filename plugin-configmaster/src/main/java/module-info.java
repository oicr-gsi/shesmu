import ca.on.oicr.gsi.shesmu.configmaster.ConfigmasterPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;

module ca.on.oicr.gsi.shesmu.plugin.configmaster {
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires ca.on.oicr.gsi.serverutils;
  requires com.fasterxml.jackson.datatype.jsr310;

  exports ca.on.oicr.gsi.shesmu.configmaster;

  provides PluginFileType with
      ConfigmasterPluginType;
}
