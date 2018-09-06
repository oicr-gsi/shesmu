package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class CurrentAlerts implements AlertSink {
	private static byte[] json = "[]".getBytes(StandardCharsets.UTF_8);

	public static void pump(OutputStream stream) throws IOException {
		stream.write(json);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public void push(byte[] alertJson) {
		json = alertJson;
	}

}
