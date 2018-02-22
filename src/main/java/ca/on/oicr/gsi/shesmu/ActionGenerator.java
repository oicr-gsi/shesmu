package ca.on.oicr.gsi.shesmu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

/**
 * User-supplied code base
 *
 * This is the bridge interface between the Java code in the server and the
 * dynamically compiled user code.
 */
public abstract class ActionGenerator {

	/**
	 * An action generator which ignores the input.
	 */
	public static final ActionGenerator NULL = new ActionGenerator() {

		@Override
		public void populateLookups(NameLoader<Lookup> loader) {
			// Do nothing.
		}

		@Override
		public void run(Consumer<Action> consumer, Supplier<Stream<Variables>> input) {
			// Do nothing.
		}

	};

	private final List<Collector> collectors = new ArrayList<>();

	@RuntimeInterop
	protected final Gauge buildGauge(String metricName, String help, String[] labelNames) {
		final Gauge g = Gauge.build("shesmu_user_" + metricName, help).labelNames(labelNames).register();
		collectors.add(g);
		return g;
	}

	/**
	 * Gather the instances of any lookups required by this action generator.
	 *
	 * The lookup instances provided must match those defined when compiled.
	 *
	 * @param loader
	 *            source of lookup instances
	 */
	@RuntimeInterop
	public abstract void populateLookups(NameLoader<Lookup> loader);

	/**
	 * Add all Prometheus monitoring for this program.
	 */
	public final void register() {
		collectors.forEach(CollectorRegistry.defaultRegistry::register);
	}

	/**
	 * Call the action generator to process the input.
	 *
	 * @param consumer
	 *            an output handler to collect and process actions as they are
	 *            created; this may be called multiple times
	 * @param input
	 *            a generator of a stream of input from the outside world; this
	 *            maybe called multiple times; the contents of the stream should
	 *            remain the value-equivalent for each call
	 */
	@RuntimeInterop
	public abstract void run(Consumer<Action> consumer, Supplier<Stream<Variables>> input);

	/**
	 * Remove all Prometheus monitoring for this program.
	 */
	public final void unregister() {
		collectors.forEach(CollectorRegistry.defaultRegistry::unregister);
	}
}
