package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

public class VidarrAnalysisFormatDefinition extends InputFormat {

  public VidarrAnalysisFormatDefinition() {
    super("vidarr_analysis", VidarrAnalysisValue.class);
  }
}
