package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class NabuFormatDefinition extends InputFormat {

  public NabuFormatDefinition() {
    super("nabu", NabuValue.class);
  }
}
