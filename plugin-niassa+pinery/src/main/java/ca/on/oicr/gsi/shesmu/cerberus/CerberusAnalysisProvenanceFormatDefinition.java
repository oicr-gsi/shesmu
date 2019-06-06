package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class CerberusAnalysisProvenanceFormatDefinition extends InputFormat {
  public CerberusAnalysisProvenanceFormatDefinition() {
    super("cerberus_ap", CerberusAnalysisProvenanceValue.class);
  }
}
