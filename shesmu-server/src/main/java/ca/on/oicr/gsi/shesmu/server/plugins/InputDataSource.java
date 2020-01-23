package ca.on.oicr.gsi.shesmu.server.plugins;

import java.util.stream.Stream;

public interface InputDataSource {
  Stream<Object> fetch(boolean readStale);
}
