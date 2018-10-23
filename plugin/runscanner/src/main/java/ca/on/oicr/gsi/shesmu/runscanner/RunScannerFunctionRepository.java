package ca.on.oicr.gsi.shesmu.runscanner;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

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
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;

@MetaInfServices
public class RunScannerFunctionRepository implements FunctionRepository {
	public final class RunScannerClient extends AutoUpdatingJsonFile<Configuration> {

		private final ConfigurationSection configurationPair;
		private final List<FunctionDefinition> functions;
		private final String instance;
		private final Map<String, String> properties = new TreeMap<>();
		private final Map<String, ObjectNode> runCache = new HashMap<>();
		private Optional<String> url = Optional.empty();

		public RunScannerClient(Path fileName) {
			super(fileName, Configuration.class);
			instance = RuntimeSupport.removeExtension(fileName, EXTENSION);
			configurationPair = new ConfigurationSection(String.format("RunScanner Function for %s", instance)) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Filename", fileName().toString());
					url.ifPresent(u -> renderer.link("URL", u, u));
				}
			};
			functions = RUNTIME_BINDING.bindFunctions(this);
		}

		public ConfigurationSection configuration() {
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
	private static final RuntimeBinding<RunScannerClient> RUNTIME_BINDING = new RuntimeBinding<>(RunScannerClient.class,
			EXTENSION)//
					.function("%s_lane_count", "laneCount", Imyhat.INTEGER,
							"Get the number of lanes detected by the Run Scanner defined in %2$s",
							new FunctionParameter("run_id", Imyhat.STRING))//
					.function("%s_read_ends", "readEnds", Imyhat.INTEGER,
							"Get the number of reads detected by the Run Scanner defined in %2$s",
							new FunctionParameter("run_id", Imyhat.STRING))//
					.function("%s_flowcell", "flowcell", Imyhat.STRING,
							"Get the serial number of the flowcell detected by the Run Scanner defined in %2$s",
							new FunctionParameter("run_id", Imyhat.STRING));
	private final AutoUpdatingDirectory<RunScannerClient> clients;

	public RunScannerFunctionRepository() {
		clients = new AutoUpdatingDirectory<>(EXTENSION, RunScannerClient::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return clients.stream().map(RunScannerClient::configuration);
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return clients.stream().flatMap(RunScannerClient::functions);
	}

}
