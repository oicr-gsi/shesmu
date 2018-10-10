package ca.on.oicr.gsi.shesmu.util.input;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.InputRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.LatencyHistogram;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public abstract class BaseJsonInputRepository<V> implements InputRepository<V> {
	public static final class Configuration {
		private long ttl;
		private String url;

		public long getTtl() {
			return ttl;
		}

		public String getUrl() {
			return url;
		}

		public void setTtl(long ttl) {
			this.ttl = ttl;
		}

		public void setUrl(String url) {
			this.url = url;
		}
	}

	private class JsonFile extends AutoUpdatingJsonFile<ObjectNode[]> {

		private List<V> values = Collections.emptyList();

		public JsonFile(Path fileName) {
			super(fileName, ObjectNode[].class);
		}

		@Override
		protected Optional<Integer> update(ObjectNode[] values) {
			this.values = Stream.of(values).map(BaseJsonInputRepository.this::convert).collect(Collectors.toList());
			return Optional.empty();
		}

		public Stream<V> variables() {
			return values.stream();
		}
	}

	private class Remote extends AutoUpdatingJsonFile<Configuration> {

		private Optional<Configuration> config = Optional.empty();
		private Instant lastUpdated = Instant.EPOCH;

		private List<V> values = Collections.emptyList();

		public Remote(Path fileName) {
			super(fileName, Configuration.class);
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			config = Optional.of(value);
			return Optional.empty();
		}

		public String url() {
			return config.map(Configuration::getUrl).orElse("");
		}

		public Stream<V> variables() {
			config.ifPresent(c -> {
				if (Duration.between(lastUpdated, Instant.now()).getSeconds() > c.getTtl()) {
					try (AutoCloseable timer = remoteJsonLatency.start(inputFormatName, c.getUrl());
							CloseableHttpResponse response = HTTP_CLIENT.execute(new HttpGet(c.getUrl()));
							JsonParser parser = RuntimeSupport.MAPPER.getFactory()
									.createParser(response.getEntity().getContent())) {
						List<V> results = new ArrayList<>();
						if (parser.nextToken() != JsonToken.START_ARRAY) {
							throw new IllegalStateException("Expected an array");
						}
						while (parser.nextToken() != JsonToken.END_ARRAY) {
							results.add(convert(RuntimeSupport.MAPPER.readTree(parser)));
						}
						if (parser.nextToken() != null) {
							throw new IllegalStateException("Junk at end of JSON document");
						}
						this.values = results;
						lastUpdated = Instant.now();
						remoteJsonUpdate.labels(inputFormatName, c.getUrl()).setToCurrentTime();
					} catch (final Exception e) {
						e.printStackTrace();
						remoteJsonError.labels(inputFormatName, c.getUrl()).inc();
					}
				}
			});
			return values.stream();
		}
	}

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
	private static final Counter remoteJsonError = Counter
			.build("shesmu_json_remote_error",
					"The number of times the fetch has failed from a remote JSON input source.")
			.labelNames("format", "target").register();

	private static final LatencyHistogram remoteJsonLatency = new LatencyHistogram("shesmu_json_remote_fetch_time",
			"The time to fetch data from a remote JSON input source.", "format", "target");

	private static final Gauge remoteJsonUpdate = Gauge
			.build("shesmu_json_remote_last_updated",
					"The last time data from the remote JSON input source was downloaded successfully.")
			.labelNames("format", "target").register();

	private final AutoUpdatingDirectory<Remote> endpoints;
	private final AutoUpdatingDirectory<JsonFile> files;
	private final String inputFormatName;

	public BaseJsonInputRepository(String inputFormatName) {
		this.inputFormatName = inputFormatName;
		files = new AutoUpdatingDirectory<>("." + inputFormatName, JsonFile::new);
		endpoints = new AutoUpdatingDirectory<>("." + inputFormatName + "-remote", Remote::new);
	}

	protected abstract V convert(ObjectNode node);

	@Override
	public final Stream<ConfigurationSection> listConfiguration() {
		return Stream.of(new ConfigurationSection(String.format("Variables from Files (%s Format)", inputFormatName)) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				files.stream()//
						.sorted(Comparator.comparing(JsonFile::fileName))//
						.forEach(file -> renderer.line(file.fileName().toString(), file.values.size()));
			}

		}, new ConfigurationSection(String.format("Variables from Remote Endpoint (%s Format)", inputFormatName)) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				endpoints.stream()//
						.sorted(Comparator.comparing(Remote::fileName))//
						.forEach(remote -> {
							renderer.line(remote.fileName().toString(), remote.url());
							renderer.lineSpan(remote.fileName().toString(), remote.lastUpdated);
						});
			}
		});
	}

	@Override
	public final Stream<V> stream() {
		return Stream.concat(files.stream().flatMap(JsonFile::variables),
				endpoints.stream().flatMap(Remote::variables));
	}

}
