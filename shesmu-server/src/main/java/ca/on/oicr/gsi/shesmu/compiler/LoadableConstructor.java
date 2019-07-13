package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;
import java.util.stream.Stream;

public interface LoadableConstructor {
  Stream<LoadableValue> create(Consumer<Renderer> baseLoader);
}
