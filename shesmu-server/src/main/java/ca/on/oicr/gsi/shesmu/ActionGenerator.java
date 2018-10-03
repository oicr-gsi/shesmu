package ca.on.oicr.gsi.shesmu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

/**
 * Superclass for user-defined
 *
 * This is the bridge interface between the Java code in the server and the
 * compiled Shesmu script. The compiler will convert a Shesmu script into a
 * subclass and can expect to call it using only what is defined in this class.
 */
public abstract class ActionGenerator {

	/**
	 * An action generator which ignores the input.
	 */
	public static final ActionGenerator NULL = new ActionGenerator() {

		@Override
		public <T> void run(ActionConsumer consumer, Function<Class<T>, Stream<T>> input) {
			// Do nothing.
		}

	};

	public static final Gauge OLIVE_FLOW = Gauge
			.build("shesmu_olive_data_flow", "The number of items passing through each olive clause.")
			.labelNames("filename", "line", "column").register();

	@RuntimeInterop
	public static final Gauge OLIVE_RUN_TIME = Gauge
			.build("shesmu_olive_run_time", "The runtime of an olive in seconds.").labelNames("filename", "line")
			.register();

	@RuntimeInterop
	public static <T> Stream<T> measureFlow(Stream<T> input, String fileName, int line, int column) {
		final Gauge.Child child = OLIVE_FLOW.labels(fileName, Integer.toString(line), Integer.toString(column));
		return input.peek(x -> child.inc());
	}

	private final List<Collector> collectors = new ArrayList<>();

	/**
	 * Create a new Prometheus monitoring gauge for this action.
	 *
	 * @param metricName
	 *            The metric name, as specified in the “Monitor” clause
	 * @param help
	 *            The help text that is associated with the metric
	 * @param labelNames
	 *            The label names for the metric. Maybe empty, but should not be
	 *            null.
	 */
	@RuntimeInterop
	protected final Gauge buildGauge(String metricName, String help, String[] labelNames) {
		final Gauge g = Gauge.build("shesmu_user_" + metricName, help).labelNames(labelNames).create();
		collectors.add(g);
		return g;
	}

	/**
	 * Add all Prometheus monitoring for this program.
	 *
	 * Only one Shemsu script may be active at any time, but multiple may be loaded
	 * in memory as part of compilation. Since duplicate Prometheus metrics are not
	 * permitted, Shesmu will {@link #unregister()} the old {@link ActionGenerator}
	 * to remove its monitoring output, then connect the new one using
	 * {@link #register()}.
	 */
	public final void register() {
		collectors.forEach(CollectorRegistry.defaultRegistry::register);
	}

	/**
	 * Call the action generator to process the input.
	 *
	 * @param consumer
	 *            an output handler to collect and process actions as they are
	 *            created; this may be called multiple times with duplicate input
	 * @param input
	 *            a generator of a stream of input from the outside world; this
	 *            maybe called multiple times; the contents of the stream should
	 *            remain the value-equivalent for each call. The order of the input
	 *            and reference-equivalence are not required. That is, multiple
	 *            calls do not have return the same objects, but should return ones
	 *            with the same content. Duplicate items are permitted, but might be
	 *            a problem for the Shesmu script.
	 */
	@RuntimeInterop
	public abstract <T> void run(ActionConsumer consumer, Function<Class<T>, Stream<T>> input);

	/**
	 * Remove all Prometheus monitoring for this program.
	 *
	 * @see #register()
	 */
	public final void unregister() {
		collectors.forEach(CollectorRegistry.defaultRegistry::unregister);
	}
}
