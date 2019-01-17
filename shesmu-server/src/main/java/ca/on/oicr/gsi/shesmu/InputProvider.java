package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

public interface InputProvider {
  <T> Stream<T> fetch(Class<T> format);
}
