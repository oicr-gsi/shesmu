import ca.on.oicr.gsi.shesmu.cerberus.CerberusErrorFormatDefinition;
import ca.on.oicr.gsi.shesmu.cerberus.CerberusPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.cerberus {
  exports ca.on.oicr.gsi.shesmu.cerberus;

  requires ca.on.oicr.gsi.cerberus;
  requires ca.on.oicr.gsi.pinery.wsdto;
  requires ca.on.oicr.gsi.provenance.api;
  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu.plugin.gsi.common;
  requires ca.on.oicr.gsi.shesmu;
  requires simpleclient;

  provides InputFormat with
      CerberusErrorFormatDefinition;
  provides PluginFileType with
      CerberusPluginType;
}
