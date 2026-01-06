package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class NabuProjectArchiveFormatDefinition extends InputFormat {

  public NabuProjectArchiveFormatDefinition() {
    super("project_archive", NabuProjectArchiveValue.class);
  }
}
