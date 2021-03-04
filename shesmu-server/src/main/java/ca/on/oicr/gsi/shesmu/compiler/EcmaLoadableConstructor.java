package ca.on.oicr.gsi.shesmu.compiler;

import java.util.stream.Stream;

public interface EcmaLoadableConstructor {
  Stream<EcmaLoadableValue> create(String base);
}
