package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

/** This input format serves Pinery sample and lane information that has not been skipped. */
@MetaInfServices
public class PineryIUSFormatDefinition extends InputFormat {
  public PineryIUSFormatDefinition() {
    super("pinery_ius", PineryIUSForAnalysisValue.class);
  }
}
