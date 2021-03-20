package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

/** This input format serves Pinery sample and lane information that has not been skipped. */
public class PineryIUSFormatDefinition extends InputFormat {
  public PineryIUSFormatDefinition() {
    super("pinery_ius", PineryIUSForAnalysisValue.class);
  }
}
