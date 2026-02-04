package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import java.lang.invoke.MethodHandles;

public final class ShesmuIntrospectionFormat extends InputFormat {

  public ShesmuIntrospectionFormat() {
    super("shesmu_actions", ShesmuIntrospectionValue.class, MethodHandles.lookup());
  }
}
