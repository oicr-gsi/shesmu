package ca.on.oicr.gsi.shesmu.plugin.input.unixfs;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class UnixFileDefinition extends InputFormat {

  public UnixFileDefinition() {
    super("unix_file", UnixFileData.class);
  }
}
