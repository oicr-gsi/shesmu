package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class CaseSummaryFormatDefinition extends InputFormat {

  public CaseSummaryFormatDefinition() {
    super("case_summary", CaseSummaryValue.class);
  }
}
