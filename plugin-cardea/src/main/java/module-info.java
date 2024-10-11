import ca.on.oicr.gsi.shesmu.cardea.CaseSummaryDetailedFormatDefinition;
import ca.on.oicr.gsi.shesmu.cardea.CaseSummaryFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.cardea {
  exports ca.on.oicr.gsi.shesmu.cardea;

  requires ca.on.oicr.gsi.shesmu;

  provides InputFormat with
      CaseSummaryFormatDefinition,
      CaseSummaryDetailedFormatDefinition;
}
