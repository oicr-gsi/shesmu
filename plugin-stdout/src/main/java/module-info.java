import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.stdout.StdoutPluginType;

module plugin.stdout {
  exports ca.on.oicr.gsi.shesmu.stdout;

  requires ca.on.oicr.gsi.shesmu;
  requires ca.on.oicr.gsi.serverutils;
  requires com.fasterxml.jackson.databind;

  provides PluginFileType with
      StdoutPluginType;
}
