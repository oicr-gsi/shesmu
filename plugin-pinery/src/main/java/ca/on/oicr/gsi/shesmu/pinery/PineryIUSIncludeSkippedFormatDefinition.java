package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

/** This input format serves all Pinery sample and lane information. */
public class PineryIUSIncludeSkippedFormatDefinition extends InputFormat {
  public PineryIUSIncludeSkippedFormatDefinition() {
    super("pinery_ius_include_skipped", PineryIUSIncludeSkippedValue.class);
  }
}
