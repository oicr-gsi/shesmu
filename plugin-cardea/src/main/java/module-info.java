import ca.on.oicr.gsi.shesmu.cardea.CardeaPluginType;
import ca.on.oicr.gsi.shesmu.cardea.CaseDetailedSummaryFormatDefinition;
import ca.on.oicr.gsi.shesmu.cardea.CaseSummaryFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.cardea {
  exports ca.on.oicr.gsi.shesmu.cardea;

  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires java.net.http;
  requires ca.on.oicr.gsi.serverutils;

  provides InputFormat with
      CaseSummaryFormatDefinition,
      CaseDetailedSummaryFormatDefinition;
  provides PluginFileType with
      CardeaPluginType;
}
