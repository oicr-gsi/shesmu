package ca.on.oicr.gsi.shesmu.runtime;

import java.util.stream.Stream;

public interface InputProvider {
  Stream<Object> fetch(String format);
}
