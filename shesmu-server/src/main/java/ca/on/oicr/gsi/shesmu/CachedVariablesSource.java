package ca.on.oicr.gsi.shesmu;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

/**
 * Load variables incrementally from a remote source
 */
public abstract class CachedVariablesSource<T> implements InputRepository<T> {

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

	private final Set<T> cache = new HashSet<>();

	private final String name;
	private final int refreshInterval;
	private volatile boolean running = true;
	private final Thread updateThread = new Thread(this::update, "variable-source");

	/**
	 * Create a new remote variable cache
	 *
	 * @param name
	 *            the name of this source, for monitoring purposes
	 * @param refreshInterval
	 *            the number of minutes to wait before refreshing
	 */
	public CachedVariablesSource(String name, int refreshInterval) {
		super();
		this.name = name;
		this.refreshInterval = refreshInterval;
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	/**
	 * Load any new data from the remote source.
	 *
	 * Duplicate items may be returned (and will be de-duplicated). This method will
	 * be called at the requested interval. The interval is the time since the last
	 * call, so if the transfer time is more than the interval, this method will not
	 * be called continuously. If no new data is available, it should return an
	 * empty stream.
	 */
	protected abstract Stream<T> more() throws Exception;

	/**
	 * Start the thread to pull from the remote source
	 */
	public final void start() {
		updateThread.start();
	}

	/**
	 * Stop the thread, interrupting any current transfer
	 */
	public final void stop() {
		running = false;
		updateThread.interrupt();
	}

	@Override
	public final Stream<T> stream() {
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
				Thread.sleep(refreshInterval * 60_000);
			} catch (final InterruptedException e) {
			}
		}
	}
}