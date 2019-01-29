package ca.on.oicr.gsi.shesmu.gsistd.input;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GsiStdFormatDefinition extends InputFormat {
  public GsiStdFormatDefinition() {
    super("gsi_std", GsiStdValue.class);
  }
}
