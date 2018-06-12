package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.prometheus.client.Counter;

/**
 * Store data that must be generated/fetched remotely and cache the results for
 * a set period of time.
 *
 * @param <K>
 *            the keys to use to lookup data in the cache
 * @param <V>
 *            the cached values
 */
public abstract class Cache<K, V> {
	private class Record {
		private final Instant fetchTime = Instant.now();
		private final K key;
		private Optional<V> value;

		public Record(K key) {
			this.key = key;
			Optional<V> value;
			try {
				value = Optional.ofNullable(fetch(key));
			} catch (final IOException e) {
				e.printStackTrace();
				value = Optional.empty();
			}
			this.value = value;
		}

		public Optional<V> refresh() {
			final Instant now = Instant.now();
			if (Duration.between(fetchTime, now).toMinutes() > ttl) {
				try {
					value = Optional.ofNullable(fetch(key));
				} catch (final IOException e) {
					e.printStackTrace();
					staleRefreshError.labels(name).inc();
				}
			}
			return value;
		}
	}

	private static final Counter staleRefreshError = Counter
			.build("shesmu_cache_refresh_error",
					"Attempted to refersh a value stored in cache, but the refresh failed.")
			.labelNames("name").register();

	private final String name;

	private final Map<K, Record> records = new HashMap<>();

	private final int ttl;

	/**
	 * Create a new cache
	 * 
	 * @param name
	 *            the name, as presented to Prometheus
	 * @param ttl
	 *            the number of minutes an item will remain in cache
	 */
	public Cache(String name, int ttl) {
		super();
		this.name = name;
		this.ttl = ttl;
	}

	/**
	 * Fetch an item from the remote service (or generate it)
	 * 
	 * @param key
	 *            the item to be requested
	 * @return the cached value
	 * @throws IOException
	 *             if an error occurs, the previous value will be retained
	 */
	protected abstract V fetch(K key) throws IOException;

	/**
	 * Get an item from cache
	 * 
	 * @param key
	 *            the key to use
	 * @return the value, if it was possible to fetch; the value may be stale if the
	 *         remote end-point is in an error state
	 */
	public final Optional<V> get(K key) {
		if (!records.containsKey(key)) {
			records.put(key, new Record(key));
		}
		return records.get(key).refresh();
	}
}
