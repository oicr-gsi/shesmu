package ca.on.oicr.gsi.shesmu.runscanner;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.net.URLCodec;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.util.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuParameter;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

public final class RunScannerClient extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {
	private class RunCache extends KeyValueCache<String, Optional<ObjectNode>> {

		public RunCache(Path fileName) {
			super("runscanner " + fileName.toString(), 15, SimpleRecord::new);
		}

		@Override
		protected Optional<ObjectNode> fetch(String runName, Instant lastUpdated) throws Exception {
			if (!url.isPresent()) {
				return Optional.empty();
			}
			final HttpGet request = new HttpGet(String.format("%s/run/%s", url.get(), new URLCodec().encode(runName)));
			try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
				if (response.getStatusLine().getStatusCode() != 200) {
					return Optional.empty();
				}
				return Optional
						.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ObjectNode.class));
			}
		}

	}

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final RunCache runCache;
	private Optional<String> url = Optional.empty();

	public RunScannerClient(Path fileName) {
		super(fileName, Configuration.class);
		runCache = new RunCache(fileName);
	}

	@ShesmuMethod(description = "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
	public String $_flowcell(@ShesmuParameter(description = "name of run") String runName) {
		return fetch(runName, "containerSerialNumber")//
				.map(JsonNode::asText)//
				.orElse("");
	}

	@ShesmuMethod(description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
	public long $_lane_count(@ShesmuParameter(description = "name of run") String runName) {
		return fetch(runName, "laneCount")//
				.filter(JsonNode::isIntegralNumber)//
				.map(JsonNode::asLong)//
				.orElse(-1L);
	}

	@ShesmuMethod(description = "Get the number of reads detected by the Run Scanner defined in {file}.")
	public long $_read_ends(@ShesmuParameter(description = "name of run") String runName) {
		return fetch(runName, "numReads")//
				.filter(JsonNode::isIntegralNumber)//
				.map(JsonNode::asLong)//
				.orElse(-1L);
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Filename", fileName().toString());
				url.ifPresent(u -> renderer.link("URL", u, u));
			}
		};
	}

	private Optional<JsonNode> fetch(String runName, String property) {
		return runCache.get(runName)//
				.filter(run -> run.has(property))//
				.map(run -> run.get("laneCount"));
	}

	@Override
	protected Optional<Integer> update(Configuration value) {
		url = Optional.ofNullable(value.getUrl());
		return Optional.empty();

	}
}