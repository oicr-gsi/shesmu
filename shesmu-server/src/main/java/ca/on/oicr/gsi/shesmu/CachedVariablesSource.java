package ca.on.oicr.gsi.shesmu;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public abstract class CachedVariablesSource implements VariablesSource {

	private static final Counter errors = Counter
			.build("shesmu_cache_source_errors", "The number of times refreshing the cache throws an error.")
			.labelNames("name").register();
	private static final Gauge lastUpdated = Gauge.build("shesmu_cache_source_last_update",
			"The last time (in seconds since the epoch) the cache was updated.").labelNames("name").register();
	private static final Gauge size = Gauge
			.build("shesmu_cache_source_size", "The number of items currently in the cache.").labelNames("name")
			.register();

	private static final LatencyHistogram updateTime = new LatencyHistogram("shesmu_cache_source_update_time",
			"The time it takes to fetch more input.", "name");

	private final Set<Variables> cache = new HashSet<>();

	private final String name;
	private volatile boolean running = true;
	private final Thread updateThread = new Thread(this::update, "variable-source");

	public CachedVariablesSource(String name) {
		super();
		this.name = name;
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	protected abstract Stream<Variables> more() throws Exception;

	public final void start() {
		updateThread.start();
	}

	public final void stop() {
		running = false;
		updateThread.interrupt();
	}

	@Override
	public final Stream<Variables> stream() {
		return cache.stream();
	}

	private void update() {
		while (running) {
			lastUpdated.labels(name).setToCurrentTime();
			try (AutoCloseable timer = updateTime.start(name)) {
				more().forEach(cache::add);
				size.labels(name).set(cache.size());
			} catch (final Exception e) {
				e.printStackTrace();
				errors.labels(name).inc();
			}
			try {
				Thread.sleep(5 * 60_000);
			} catch (final InterruptedException e) {
			}
		}
	}

}
