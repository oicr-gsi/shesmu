package ca.on.oicr.gsi.shesmu.gsicommon;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class CerberusFileProvenanceFormatDefinition extends InputFormat {
  public CerberusFileProvenanceFormatDefinition() {
    super("cerberus_fp", CerberusFileProvenanceValue.class);
  }
}
