import ca.on.oicr.gsi.shesmu.pinery.PineryIUSFormatDefinition;
import ca.on.oicr.gsi.shesmu.pinery.PineryIUSIncludeSkippedFormatDefinition;
import ca.on.oicr.gsi.shesmu.pinery.PineryPluginType;
import ca.on.oicr.gsi.shesmu.pinery.PineryProjectFormatDefinition;
import ca.on.oicr.gsi.shesmu.pinery.barcodes.BarcodeGrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module shesmu.plugin.pinery {
  exports ca.on.oicr.gsi.shesmu.pinery;
  exports ca.on.oicr.gsi.shesmu.pinery.barcodes;

  requires ca.on.oicr.gsi.pinery.wsdto;
  requires ca.on.oicr.gsi.provenance.api;
  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu.plugin.gsi.common;
  requires ca.on.oicr.gsi.shesmu.plugin.runscanner;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires simpleclient;
  requires java.net.http;
  requires org.apache.commons.text;

  provides GrouperDefinition with
      BarcodeGrouperDefinition;
  provides InputFormat with
      PineryIUSFormatDefinition,
      PineryIUSIncludeSkippedFormatDefinition,
      PineryProjectFormatDefinition;
  provides PluginFileType with
      PineryPluginType;
}
