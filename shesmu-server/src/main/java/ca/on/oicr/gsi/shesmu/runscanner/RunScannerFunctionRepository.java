package ca.on.oicr.gsi.shesmu.runscanner;

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
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.function.FunctionForInstance;
import io.prometheus.client.Counter;

@MetaInfServices
public class RunScannerFunctionRepository implements FunctionRepository {
	private class RunScannerClient extends AutoUpdatingJsonFile<Configuration> {

		private final Pair<String, Map<String, String>> configurationPair;
		private final String instance;
		private final FunctionDefinition laneCount;
		private final Map<String, Long> laneCountCache = new HashMap<>();
		private final Map<String, String> properties = new TreeMap<>();
		private Optional<String> url = Optional.empty();

		public RunScannerClient(Path fileName) {
			super(fileName, Configuration.class);
			instance = RuntimeSupport.removeExtension(fileName, EXTENSION);
			configurationPair = new Pair<>(String.format("RunScanner Function for %s", instance), properties);
			FunctionDefinition laneCount;
			try {
				laneCount = FunctionForInstance.bind(MethodHandles.lookup(), RunScannerClient.class, this, "laneCount",
						String.format("%s_lane_count", instance),
						String.format("Get the number of lanes detected by the Run Scanner defined in %s", fileName),
						Imyhat.INTEGER, Imyhat.STRING);
			} catch (NoSuchMethodException | IllegalAccessException e) {
				laneCount = null;
				e.printStackTrace();
			}
			this.laneCount = laneCount;
		}

		public Pair<String, Map<String, String>> configuration() {
			return configurationPair;
		}

		public Stream<FunctionDefinition> functions() {
			return laneCount == null ? Stream.empty() : Stream.of(laneCount);
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

	public RunScannerFunctionRepository() {
		clients = RuntimeSupport.dataFiles(EXTENSION).map(RunScannerClient::new).peek(RunScannerClient::start)
				.collect(Collectors.toList());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return clients.stream().map(RunScannerClient::configuration);
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return clients.stream().flatMap(RunScannerClient::functions);
	}

}
