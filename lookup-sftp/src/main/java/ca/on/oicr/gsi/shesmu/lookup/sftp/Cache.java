package ca.on.oicr.gsi.shesmu.lookup.sftp;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.prometheus.client.Counter;

public abstract class Cache<T> {
	private class Record {
		private Instant fetchTime = Instant.now();
		private final String filename;
		private Optional<T> value;

		public Record(String filename) {
			this.filename = filename;
			Optional<T> value;
			try {
				value = Optional.ofNullable(fetch(filename));
			} catch (IOException e) {
				e.printStackTrace();
				value = Optional.empty();
			}
			this.value = value;
		}

		public Optional<T> refresh() {
			Instant now = Instant.now();
			if (Duration.between(fetchTime, now).toMinutes() > 10) {
				try {
					value = Optional.ofNullable(fetch(filename));
				} catch (IOException e) {
					e.printStackTrace();
					staleRefreshError.labels(name).inc();
				}
			}
			return value;
		}
	}

	private static final Counter staleRefreshError = Counter
			.build("shesmu_sftp_cache_refresh_error",
					"Attempted to refersh a value stored in cache, but the refresh failed.")
			.labelNames("name").register();

	private final String name;

	private final Map<String, Record> records = new HashMap<>();

	public Cache(String name) {
		super();
		this.name = name;
	}

	protected abstract T fetch(String fileName) throws IOException;

	public Optional<T> get(String fileName) {
		if (!records.containsKey(fileName)) {
			records.put(fileName, new Record(fileName));
		}
		return records.get(fileName).refresh();
	}
}
