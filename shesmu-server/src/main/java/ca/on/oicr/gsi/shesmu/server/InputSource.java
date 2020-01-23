package ca.on.oicr.gsi.shesmu.server;

import java.util.stream.Stream;

public interface InputSource {
  Stream<Object> fetch(String format, boolean readStale);
}
