import ca.on.oicr.gsi.shesmu.guanyin.GuanyinReportInputFormat;
import ca.on.oicr.gsi.shesmu.guanyin.GuanyinReportPluginType;
import ca.on.oicr.gsi.shesmu.onlinereport.OnlineReportPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.guanyin {
  exports ca.on.oicr.gsi.shesmu.guanyin;
  exports ca.on.oicr.gsi.shesmu.onlinereport;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;
  requires java.net.http;

  provides InputFormat with
      GuanyinReportInputFormat;
  provides PluginFileType with
      GuanyinReportPluginType,
      OnlineReportPluginType;
}
