package ca.on.oicr.gsi.shesmu.runscanner;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.util.function.FunctionForInstance;
import io.prometheus.client.Counter;

@MetaInfServices
public class RunScannerFunctionRepository implements FunctionRepository {
	private class RunScannerClient extends AutoUpdatingJsonFile<Configuration> {

		private final Pair<String, Map<String, String>> configurationPair;
		private final List<FunctionDefinition> functions;
		private final String instance;
		private final Map<String, String> properties = new TreeMap<>();
		private final Map<String, ObjectNode> runCache = new HashMap<>();
		private Optional<String> url = Optional.empty();

		public RunScannerClient(Path fileName) {
			super(fileName, Configuration.class);
			instance = RuntimeSupport.removeExtension(fileName, EXTENSION);
			configurationPair = new Pair<>(String.format("RunScanner Function for %s", instance), properties);
			List<FunctionDefinition> functions;
			try {
				functions = Arrays.asList(//
						FunctionForInstance.bind(MethodHandles.lookup(), RunScannerClient.class, this, "laneCount",
								String.format("%s_lane_count", instance),
								String.format("Get the number of lanes detected by the Run Scanner defined in %s",
										fileName),
								Imyhat.INTEGER, new FunctionParameter("run_id", Imyhat.STRING)), //
						FunctionForInstance.bind(MethodHandles.lookup(), RunScannerClient.class, this, "readEnds",
								String.format("%s_read_ends", instance),
								String.format("Get the number of reads detected by the Run Scanner defined in %s",
										fileName),
								Imyhat.INTEGER, new FunctionParameter("run_id", Imyhat.STRING)), //
						FunctionForInstance.bind(MethodHandles.lookup(), RunScannerClient.class, this, "flowcell",
								String.format("%s_flowcell", instance),
								String.format(
										"Get the serial number of the flowcell detected by the Run Scanner defined in %s",
										fileName),
								Imyhat.STRING, new FunctionParameter("run_id", Imyhat.STRING))//
				);
			} catch (NoSuchMethodException | IllegalAccessException e) {
				functions = Collections.emptyList();
				e.printStackTrace();
			}
			this.functions = functions;
		}

		public Pair<String, Map<String, String>> configuration() {
			return configurationPair;
		}

		private Optional<ObjectNode> fetch(String runName) {
			if (runCache.containsKey(runName)) {
				return Optional.of(runCache.get(runName));
			}
			return url.flatMap(u -> {
				final HttpGet request = new HttpGet(String.format("%s/run/%s", u, runName));
				try (AutoCloseable timer = requestTime.start(u);
						CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
					if (response.getStatusLine().getStatusCode() != 200) {
						requestErrors.labels(u).inc();
						return Optional.empty();
					}
					final ObjectNode run = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
							ObjectNode.class);
					runCache.put(runName, run);
					return Optional.of(run);
				} catch (final Exception e) {
					e.printStackTrace();
					requestErrors.labels(u).inc();
					return Optional.empty();
				}
			});

		}

		@RuntimeInterop
		public String flowcell(String runName) {
			return fetch(runName).map(run -> {
				if (run.has("containerSerialNumber")) {
					return run.get("containerSerialNumber").asText();
				}
				return "";
			}).orElse("");
		}

		public Stream<FunctionDefinition> functions() {
			return functions.stream();
		}

		@RuntimeInterop
		public long laneCount(String runName) {
			return fetch(runName).map(run -> {
				if (run.has("laneCount")) {
					final JsonNode laneCount = run.get("laneCount");
					if (laneCount.isIntegralNumber()) {
						final long count = laneCount.asLong();
						return count;
					}
				}
				return -1L;
			}).orElse(-1L);
		}

		@RuntimeInterop
		public long readEnds(String runName) {
			return fetch(runName).map(run -> {
				if (run.has("numReads")) {
					final JsonNode laneCount = run.get("numReads");
					if (laneCount.isIntegralNumber()) {
						final long count = laneCount.asLong();
						return count;
					}
				}
				return -1L;
			}).orElse(-1L);
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			url = Optional.ofNullable(value.getUrl());
			properties.put("url", value.getUrl());
			return Optional.empty();

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

	private final AutoUpdatingDirectory<RunScannerClient> clients;

	public RunScannerFunctionRepository() {
		clients = new AutoUpdatingDirectory<>(EXTENSION, RunScannerClient::new);
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
