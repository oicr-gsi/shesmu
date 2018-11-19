package ca.on.oicr.gsi.shesmu.util.input;

import java.nio.file.Path;
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
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.util.cache.ReplacingRecord;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

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
		private class RemoteReloader extends ValueCache<Stream<V>> {
			public RemoteReloader(Path fileName) {
				super("remotejson " + inputFormatName + " " + fileName.toString(), 10, ReplacingRecord::new);
			}

			@Override
			protected Stream<V> fetch(Instant lastUpdated) throws Exception {
				if (!config.isPresent())
					return Stream.empty();
				final String url = config.map(Configuration::getUrl).get();
				try (CloseableHttpResponse response = HTTP_CLIENT.execute(new HttpGet(url));
						JsonParser parser = RuntimeSupport.MAPPER.getFactory()
								.createParser(response.getEntity().getContent())) {
					final List<V> results = new ArrayList<>();
					if (parser.nextToken() != JsonToken.START_ARRAY) {
						throw new IllegalStateException("Expected an array");
					}
					while (parser.nextToken() != JsonToken.END_ARRAY) {
						results.add(convert(RuntimeSupport.MAPPER.readTree(parser)));
					}
					if (parser.nextToken() != null) {
						throw new IllegalStateException("Junk at end of JSON document");
					}
					return results.stream();
				}
			}
		}

		private final ValueCache<Stream<V>> cache;
		private Optional<Configuration> config = Optional.empty();

		public Remote(Path fileName) {
			super(fileName, Configuration.class);
			cache = new RemoteReloader(fileName);
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			config = Optional.of(value);
			cache.invalidate();
			return Optional.empty();
		}

		public String url() {
			return config.map(Configuration::getUrl).orElse("");
		}

		public Stream<V> variables() {
			return cache.get();
		}
	}

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

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
							renderer.lineSpan(remote.fileName().toString(), remote.cache.lastUpdated());
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
