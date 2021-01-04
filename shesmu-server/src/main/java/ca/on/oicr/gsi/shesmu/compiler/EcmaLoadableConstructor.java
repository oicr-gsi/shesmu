package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Function;
import java.util.stream.Stream;

public interface EcmaLoadableConstructor {
  Stream<EcmaLoadableValue> create(Function<EcmaScriptRenderer, String> baseLoader);
}
