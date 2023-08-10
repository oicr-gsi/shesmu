package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class NabuFileQcFormatDefinition extends InputFormat {

  public NabuFileQcFormatDefinition() {
    super("nabu_file_qc", NabuFileQcValue.class);
  }
}
