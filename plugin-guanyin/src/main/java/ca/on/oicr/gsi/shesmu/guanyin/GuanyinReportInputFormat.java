package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GuanyinReportInputFormat extends InputFormat {

  public GuanyinReportInputFormat() {
    super("guanyin_report", GuanyinReportValue.class);
  }
}
