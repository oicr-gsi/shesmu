package ca.on.oicr.gsi.shesmu;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.prometheus.client.Gauge;

@MetaInfServices
public class EmergencyThrottler implements Throttler {

	private static final Gauge stopGauge = Gauge
			.build("shesmu_emergency_throttler", "Whether the emergency throttler is engaged.").register();
	private static volatile boolean stopped;

	public static void set(boolean stopped) {
		EmergencyThrottler.stopped = stopped;
		stopGauge.set(stopped ? 1 : 0);
	}

	public static boolean stopped() {
		return stopped;
	}

	@Override
	public boolean isOverloaded(Set<String> services) {
		return stopped;
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

}
