import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.runscanner.LaneSplittingGrouperDefinition;
import ca.on.oicr.gsi.shesmu.runscanner.RunScannerPluginType;

module ca.on.oicr.gsi.shesmu.plugin.runscanner {
  exports ca.on.oicr.gsi.shesmu.runscanner;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires java.net.http;
  requires runscanner.dto;

  provides GrouperDefinition with
      LaneSplittingGrouperDefinition;
  provides PluginFileType with
      RunScannerPluginType;
}
