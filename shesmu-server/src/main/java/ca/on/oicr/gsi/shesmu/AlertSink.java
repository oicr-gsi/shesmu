package ca.on.oicr.gsi.shesmu;

import java.util.ServiceLoader;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;

public interface AlertSink extends LoadedConfiguration {
	public static final ServiceLoader<AlertSink> SINKS = ServiceLoader.load(AlertSink.class);

	public static Stream<AlertSink> sinks() {
		return RuntimeSupport.stream(SINKS);
	}

	void push(byte[] alertJson);
}
