package ca.on.oicr.gsi.shesmu;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.prometheus.client.Gauge;

/**
 * Caches the results of a streaming process and updates it periodically
 *
 * @author amasella
 *
 * @param <I>
 *            The interface to access using a {@link ServiceLoader}
 * @param <T>
 *            The types in the stream
 */
public final class CachedRepository<I, T> {
	private static final Gauge cacheSize = Gauge
			.build("cached_repository_size", "The number of items stored in a cached repository.").labelNames("type")
			.register();
	private static final Gauge implementationCount = Gauge
			.build("cached_repository_implementations", "The number of implementations in a cached repository.")
			.labelNames("type").register();
	private static final LatencyHistogram lockedTime = new LatencyHistogram("cached_repository_locked_time",
			"The time, in seconds, the repository is locked.", "type");
	private static final LatencyHistogram populationTime = new LatencyHistogram("cached_repository_population_time",
			"The time, in seconds, the repository takes to repopulate.", "type");

	private final Function<I, Stream<T>> flatMapper;

	private List<T> items = Collections.emptyList();

	private Instant lastUpdated = Instant.EPOCH;

	private final Semaphore semaphore = new Semaphore(1);

	private final Class<I> serviceClass;

	private final ServiceLoader<I> serviceLoader;

	public CachedRepository(Class<I> serviceClass, Function<I, Stream<T>> flatMapper) {
		this.serviceClass = serviceClass;
		this.flatMapper = flatMapper;
		serviceLoader = ServiceLoader.load(serviceClass);
		implementationCount.labels(serviceClass.getCanonicalName()).set(implementations().count());
	}

	/**
	 * Get the known implementations of the interface from the service loader.
	 */
	public Stream<I> implementations() {
		return StreamSupport.stream(serviceLoader.spliterator(), false);
	}

	/**
	 * Get the combined items for all the known implementations
	 *
	 * If the cache is old enough, the implementations are queried again; if not,
	 * the cached results are returned.
	 */
	public Stream<T> stream() {
		List<T> current;
		try (AutoCloseable timer = lockedTime.start(serviceClass.getCanonicalName())) {
			semaphore.acquireUninterruptibly();
			if (Duration.between(lastUpdated, Instant.now()).toMinutes() > 15) {
				try (AutoCloseable populationTimer = populationTime.start(serviceClass.getCanonicalName())) {
					items = implementations().flatMap(flatMapper).collect(Collectors.toList());
				}
				lastUpdated = Instant.now();
				cacheSize.labels(serviceClass.getCanonicalName()).set(items.size());
			}
			current = items;
		} catch (final Exception e) {
			current = Collections.emptyList();
			e.printStackTrace();
		} finally {
			semaphore.release();
		}
		return current.stream();
	}
}
