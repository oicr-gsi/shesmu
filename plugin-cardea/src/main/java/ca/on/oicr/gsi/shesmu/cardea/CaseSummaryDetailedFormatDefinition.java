package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CaseSummaryDetailedFormatDefinition extends InputFormat {

  public CaseSummaryDetailedFormatDefinition() {
    super("case_detailed_summary", CaseSummaryDetailedValue.class);
  }
}
