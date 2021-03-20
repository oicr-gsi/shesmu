package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class NabuCaseArchiveFormatDefinition extends InputFormat {

  public NabuCaseArchiveFormatDefinition() {
    super("case_archive", NabuCaseArchiveValue.class);
  }
}
