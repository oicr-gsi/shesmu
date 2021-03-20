package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Write the value of a constant into the <code>value</code> property of a JSON object. */
public interface ConstantLoader {

  @RuntimeInterop
  void load(ObjectNode target);
}
