package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class VidarrProvenanceDefinition extends InputFormat {

  public VidarrProvenanceDefinition() {
    super("vidarr_analysis", VidarrProvenanceValue.class);
  }
}
