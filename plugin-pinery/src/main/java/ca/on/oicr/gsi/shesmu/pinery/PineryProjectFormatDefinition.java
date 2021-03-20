package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class PineryProjectFormatDefinition extends InputFormat {
  public PineryProjectFormatDefinition() {
    super("pinery_project", PineryProjectValue.class);
  }
}
