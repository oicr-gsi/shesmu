package ca.on.oicr.gsi.shesmu.gsistd.input;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class CerberusFileProvenanceFormatDefinition extends InputFormat {
  public CerberusFileProvenanceFormatDefinition() {
    super("cerberus_fp", CerberusFileProvenanceValue.class);
  }
}
