package ca.on.oicr.gsi.shesmu.server.plugins;

import java.io.InputStream;

public interface DynamicInputJsonSource {
  InputStream fetch(Object instance);
}
