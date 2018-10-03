package ca.on.oicr.gsi.shesmu.core.prometheus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AlertSink;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import io.prometheus.client.Gauge;

/**
 * Sends alerts to Prometheus Alert Manager
 */
@MetaInfServices(AlertSink.class)
public class PrometheusAlertSink implements AlertSink {
	private static final Gauge pushOk = Gauge.build("shesmu_alert_push_alertman_good",
			"Whether the last push of alerts to Alert Manager was successful.").labelNames("target").register();

	private class Endpoint extends AutoUpdatingJsonFile<Configuration> {

		private Optional<Configuration> configuration = Optional.empty();

		public Endpoint(Path fileName) {
			super(fileName, Configuration.class);
		}

		public Pair<String, Map<String, String>> configuration() {
			final Map<String, String> properties = new TreeMap<>();
			configuration.ifPresent(c -> {
				properties.put("address", c.getAlertmanager());
			});

			return new Pair<>("Prometheus Alert Sink: " + fileName().toString(), properties);
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			configuration = Optional.of(value);
			return Optional.empty();
		}

		public void push(byte[] alertJson) {
			configuration.ifPresent(config -> {
				HttpPost request = new HttpPost(String.format("%s/api/v1/alerts", config.getAlertmanager()));
				request.addHeader("Content-type", ContentType.APPLICATION_JSON.getMimeType());
				request.setEntity(new ByteArrayEntity(alertJson));
				try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
					boolean ok = response.getStatusLine().getStatusCode() != 200;
					if (ok) {
						System.err.printf("Failed to write alerts to %s: %d %s", config.getAlertmanager(),
								response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
					}
					pushOk.labels(config.getAlertmanager()).set(ok ? 1 : 0);
				} catch (IOException e) {
					e.printStackTrace();
					pushOk.labels(config.getAlertmanager()).set(0);
				}
			});
		}

	}

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final AutoUpdatingDirectory<Endpoint> configuration = new AutoUpdatingDirectory<>(".alertman",
			Endpoint::new);

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configuration.stream().map(Endpoint::configuration);
	}

	@Override
	public void push(byte[] alertJson) {
		configuration.stream().forEach(endpoint -> endpoint.push(alertJson));
	}

}
