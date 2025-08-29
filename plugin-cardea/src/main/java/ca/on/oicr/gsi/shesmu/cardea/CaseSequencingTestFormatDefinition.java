package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CaseSequencingTestFormatDefinition extends InputFormat {
  public CaseSequencingTestFormatDefinition() {
    super("case_sequencing_test", SequencingTestValue.class);
  }
}
