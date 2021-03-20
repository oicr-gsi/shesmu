package ca.on.oicr.gsi.shesmu.gsicommon;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CerberusFileProvenanceSkippedFormatDefinition extends InputFormat {
  public CerberusFileProvenanceSkippedFormatDefinition() {
    super("cerberus_fp_skipped", CerberusFileProvenanceSkippedValue.class);
  }
}
