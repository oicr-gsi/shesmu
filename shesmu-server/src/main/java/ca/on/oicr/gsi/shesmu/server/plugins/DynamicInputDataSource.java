package ca.on.oicr.gsi.shesmu.server.plugins;

import java.util.stream.Stream;

public interface DynamicInputDataSource {
  Stream<Object> fetch(Object instance, boolean readStale);
}
