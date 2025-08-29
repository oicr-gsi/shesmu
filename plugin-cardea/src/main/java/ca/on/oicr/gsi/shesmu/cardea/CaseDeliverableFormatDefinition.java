package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CaseDeliverableFormatDefinition extends InputFormat {

  public CaseDeliverableFormatDefinition() {
    super("case_deliverable", DeliverableValue.class);
  }
}
