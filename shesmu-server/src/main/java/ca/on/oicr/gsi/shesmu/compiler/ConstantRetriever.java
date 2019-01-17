package ca.on.oicr.gsi.shesmu.compiler;

import java.util.stream.Stream;

public interface ConstantRetriever {
  Stream<? extends Target> get(boolean allowUserDefined);
}
