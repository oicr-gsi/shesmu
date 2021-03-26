package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import java.lang.invoke.MethodHandles;
import org.kohsuke.MetaInfServices;

@MetaInfServices(InputFormat.class)
public final class ShesmuIntrospectionFormat extends InputFormat {

  public ShesmuIntrospectionFormat() {
    super("shesmu", ShesmuIntrospectionValue.class, MethodHandles.lookup());
  }
}
