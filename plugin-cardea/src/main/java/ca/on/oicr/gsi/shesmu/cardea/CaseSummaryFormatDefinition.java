package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CaseSummaryFormatDefinition extends InputFormat {

  public CaseSummaryFormatDefinition() {
    super("case_summary", CaseSummaryValue.class);
  }
}
