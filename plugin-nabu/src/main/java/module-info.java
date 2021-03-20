import ca.on.oicr.gsi.shesmu.nabu.NabuCaseArchiveFormatDefinition;
import ca.on.oicr.gsi.shesmu.nabu.NabuFileQcFormatDefinition;
import ca.on.oicr.gsi.shesmu.nabu.NabuPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.nabu {
  exports ca.on.oicr.gsi.shesmu.nabu;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires java.net.http;

  provides InputFormat with
      NabuCaseArchiveFormatDefinition,
      NabuFileQcFormatDefinition;
  provides PluginFileType with
      NabuPluginType;
}
