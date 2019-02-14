package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class PineryProjectFormatDefinition extends InputFormat {
  public PineryProjectFormatDefinition() {
    super("pinery_project", PineryProjectValue.class);
  }
}
