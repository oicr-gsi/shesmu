package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public interface AlertSink extends LoadedConfiguration {
  public static final ServiceLoader<AlertSink> SINKS = ServiceLoader.load(AlertSink.class);

  public static Stream<AlertSink> sinks() {
    return RuntimeSupport.stream(SINKS);
  }

  void push(byte[] alertJson);
}
