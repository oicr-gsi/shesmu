package ca.on.oicr.gsi.shesmu.throttler.ratelimit;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Throttler;
import io.prometheus.client.Gauge;

@MetaInfServices
public class RateLimitThrottler implements Throttler {
	private class TokenBucket extends AutoUpdatingJsonFile<Configuration> {

		private int capacity;

		private int delay = 10_000;
		private Instant lastTime = Instant.now();
		private final String service;
		private int tokens;

		public TokenBucket(Path fileName) {
			super(fileName, Configuration.class);
			service = RuntimeSupport.removeExtension(fileName, EXTENSION);
		}

		public synchronized boolean checkCapacity(Set<String> services) {
			if (!services.contains(service)) {
				return true;
			}
			final int newTokens = (int) (Duration.between(lastTime, Instant.now()).toMillis() / delay);
			lastTime = lastTime.plusMillis(delay * newTokens);
			tokens = Math.min(tokens + newTokens, capacity);
			tokenCount.labels(service).set(tokens);
			return tokens > 0;
		}

		public Pair<String, Map<String, String>> configuration() {
			final Map<String, String> properties = new TreeMap<>();
			properties.put("capacity", Integer.toString(capacity));
			properties.put("regeneration delay (ms)", Integer.toString(delay));
			return new Pair<>(String.format("%s Rate Limiter", service), properties);
		}

		public synchronized void decrement() {
			tokens--;
		}

		@Override
		protected synchronized Optional<Integer> update(Configuration value) {
			capacity = Math.max(0, value.getCapacity());
			delay = Math.max(1, value.getDelay());
			tokens = Math.min(tokens, capacity);
			bucketCapacity.labels(service).set(capacity);
			rechargeDelay.labels(service).set(delay);
			return Optional.empty();
		}

	}

	private static final Gauge bucketCapacity = Gauge
			.build("shesmu_throttler_ratelimit_capacity", "The maximum number of tokens in the bucket.")
			.labelNames("service").register();
	private static final String EXTENSION = ".ratelimit";
	private static final Gauge rechargeDelay = Gauge
			.build("shesmu_throttler_ratelimit_recharge_delay", "The number of milliseconds to generate a new token.")
			.labelNames("service").register();

	private static final Gauge tokenCount = Gauge
			.build("shesmu_throttler_ratelimit_tokens", "The number of tokens in the bucket.").labelNames("service")
			.register();

	private final AutoUpdatingDirectory<TokenBucket> buckets;

	public RateLimitThrottler() {
		buckets = new AutoUpdatingDirectory<>(EXTENSION, TokenBucket::new);
	}

	@Override
	public synchronized boolean isOverloaded(Set<String> services) {
		if (buckets.isEmpty()) {
			return false;
		}
		if (buckets.stream().allMatch(bucket -> bucket.checkCapacity(services))) {
			buckets.stream().forEach(TokenBucket::decrement);
			return false;
		}
		return true;
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return buckets.stream().map(TokenBucket::configuration);
	}

}
