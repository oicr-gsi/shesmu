package ca.on.oicr.gsi.shesmu.lookup.runscanner;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.lookup.LookupForInstance;
import io.prometheus.client.Counter;

@MetaInfServices
public class RunScannerLookupRepository implements LookupRepository {
	private class RunScannerClient extends AutoUpdatingJsonFile<Configuration> {

		private final Pair<String, Map<String, String>> configurationPair;
		private final String instance;
		private final LookupDefinition laneCount;
		private final Map<String, Long> laneCountCache = new HashMap<>();
		private final Map<String, String> properties = new TreeMap<>();
		private Optional<String> url = Optional.empty();

		public RunScannerClient(Path fileName) {
			super(fileName, Configuration.class);
			final String fileNamePart = fileName.getFileName().toString();
			instance = fileNamePart.substring(0, fileNamePart.length() - EXTENSION.length());
			configurationPair = new Pair<>(String.format("RunScanner Lookup for %s", instance), properties);
			LookupDefinition laneCount;
			try {
				laneCount = LookupForInstance.bind(MethodHandles.lookup(), RunScannerClient.class, this, "laneCount",
						String.format("%s_lane_count", instance), Imyhat.INTEGER, Imyhat.STRING);
			} catch (NoSuchMethodException | IllegalAccessException e) {
				laneCount = null;
				e.printStackTrace();
			}
			this.laneCount = laneCount;
		}

		public Pair<String, Map<String, String>> configuration() {
			return configurationPair;
		}

		@RuntimeInterop
		public long laneCount(String runName) {
			if (laneCountCache.containsKey(runName)) {
				return laneCountCache.get(runName);
			}
			return url.<Long>map(u -> {
				final HttpGet request = new HttpGet(String.format("%s/run/%s", u, runName));
				try (AutoCloseable timer = requestTime.start(u);
						CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
					if (response.getStatusLine().getStatusCode() != 200) {
						requestErrors.labels(u).inc();
						return -1L;
					}
					final ObjectNode result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
							ObjectNode.class);
					if (result.has("laneCount")) {
						final JsonNode laneCount = result.get("laneCount");
						if (laneCount.isIntegralNumber()) {
							final long count = laneCount.asLong();
							laneCountCache.put(runName, count);
							return count;
						}
					}
					return -1L;
				} catch (final Exception e) {
					e.printStackTrace();
					requestErrors.labels(u).inc();
					return -1L;
				}
			}).orElse(-1L);
		}

		public Stream<LookupDefinition> lookups() {
			return laneCount == null ? Stream.empty() : Stream.of(laneCount);
		}

		@Override
		protected void update(Configuration value) {
			url = Optional.ofNullable(value.getUrl());
			properties.put("url", value.getUrl());
		}
	}

	private static final String EXTENSION = ".runscanner";
	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
	private static final Counter requestErrors = Counter
			.build("shesmu_runscanner_request_errors",
					"The number of errors trying to countact the RunScanner web service.")
			.labelNames("target").register();

	private static final LatencyHistogram requestTime = new LatencyHistogram("shesmu_runscanner_request_time",
			"The request time latency to access run information.", "target");

	private final List<RunScannerClient> clients;

	public RunScannerLookupRepository() {
		clients = RuntimeSupport.dataFiles(EXTENSION).map(RunScannerClient::new).collect(Collectors.toList());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return clients.stream().map(RunScannerClient::configuration);
	}

	@Override
	public Stream<LookupDefinition> queryLookups() {
		return clients.stream().flatMap(RunScannerClient::lookups);
	}

}
