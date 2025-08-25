package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CaseDeliverablesFormatDefinition extends InputFormat {

  public CaseDeliverablesFormatDefinition() {
    super("case_deliverables", DeliverableValue.class);
  }
}
