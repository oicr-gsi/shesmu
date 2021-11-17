package ca.on.oicr.gsi.shesmu.gsicommon;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class CerberusFileProvenanceSkippedFormatDefinition extends InputFormat {
  public CerberusFileProvenanceSkippedFormatDefinition() {
    super("cerberus_fp_skipped", CerberusFileProvenanceSkippedValue.class);
  }
}
