package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class CerberusErrorFormatDefinition extends InputFormat {

  public CerberusErrorFormatDefinition() {
    super("cerberus_error", CerberusErrorValue.class);
  }
}
