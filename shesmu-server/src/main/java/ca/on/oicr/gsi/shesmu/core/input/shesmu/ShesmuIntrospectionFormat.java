package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import org.kohsuke.MetaInfServices;

@MetaInfServices(InputFormat.class)
public class ShesmuIntrospectionFormat extends InputFormat {

  public ShesmuIntrospectionFormat() {
    super("shesmu", ShesmuIntrospectionValue.class);
  }
}
