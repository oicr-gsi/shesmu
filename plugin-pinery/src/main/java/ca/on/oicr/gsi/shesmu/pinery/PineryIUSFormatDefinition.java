package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class PineryIUSFormatDefinition extends InputFormat {
  public PineryIUSFormatDefinition() {
    super("pinery_ius", PineryIUSValue.class);
  }
}
