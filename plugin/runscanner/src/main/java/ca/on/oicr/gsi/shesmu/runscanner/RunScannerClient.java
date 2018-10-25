package ca.on.oicr.gsi.shesmu.runscanner;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuParameter;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;

public final class RunScannerClient extends AutoUpdatingJsonFile<Configuration>
		implements FileBackedConfiguration {
	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
	private static final Counter requestErrors = Counter
			.build("shesmu_runscanner_request_errors",
					"The number of errors trying to countact the RunScanner web service.")
			.labelNames("target").register();

	private static final LatencyHistogram requestTime = new LatencyHistogram("shesmu_runscanner_request_time",
			"The request time latency to access run information.", "target");
	private final ConfigurationSection configurationPair;
	private final Map<String, String> properties = new TreeMap<>();
	private final Map<String, ObjectNode> runCache = new HashMap<>();
	private Optional<String> url = Optional.empty();

	public RunScannerClient(Path fileName) {
		super(fileName, Configuration.class);
		configurationPair = new ConfigurationSection("RunScanner: " + fileName()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Filename", fileName().toString());
				url.ifPresent(u -> renderer.link("URL", u, u));
			}
		};
	}

	@ShesmuMethod(description = "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
	public String $_flowcell(@ShesmuParameter(description = "name of run") String runName) {
		return fetch(runName).map(run -> {
			if (run.has("containerSerialNumber")) {
				return run.get("containerSerialNumber").asText();
			}
			return "";
		}).orElse("");
	}

	@ShesmuMethod(description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
	public long $_lane_count(@ShesmuParameter(description = "name of run") String runName) {
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

	@ShesmuMethod(description = "Get the number of reads detected by the Run Scanner defined in {file}.")
	public long $_read_ends(@ShesmuParameter(description = "name of run") String runName) {
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

	@Override
	protected Optional<Integer> update(Configuration value) {
		url = Optional.ofNullable(value.getUrl());
		properties.put("url", value.getUrl());
		return Optional.empty();

	}
}