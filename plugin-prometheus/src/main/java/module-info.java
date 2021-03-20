import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.prometheus.PrometheusAlertManagerPluginType;

module ca.on.oicr.gsi.shesmu.plugin.prometheus {
  exports ca.on.oicr.gsi.shesmu.prometheus;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;
  requires java.net.http;

  provides PluginFileType with
      PrometheusAlertManagerPluginType;
}
