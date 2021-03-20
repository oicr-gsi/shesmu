package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class NabuFormatDefinition extends InputFormat {

  public NabuFormatDefinition() {
    super("nabu", NabuValue.class);
  }
}
