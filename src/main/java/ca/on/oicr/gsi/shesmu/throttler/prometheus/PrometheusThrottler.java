package ca.on.oicr.gsi.shesmu.throttler.prometheus;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Throttler;
import io.prometheus.client.Gauge;

/**
 * Determines if throttling should occur based on a Prometheus Alert Manager
 *
 * It assumes that there will be an alert firing:
 *
 * <pre>
 * AutoInhibit{environment="e",job="s"}
 * </pre>
 *
 * where <i>e</i> matches the environment variable <i>SHESMU_ENVIONMENT</i> (or
 * is absent) and <i>s</i> is one of the services specified by the action.
 */
@MetaInfServices(Throttler.class)
public class PrometheusThrottler implements Throttler {
	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
	private static final Gauge lastUpdated = Gauge
			.build("shesmu_prom_throttler_last_update", "The last time the data was fetched.").register();

	private static final Gauge ok = Gauge
			.build("shesmu_prom_throttler_status", "Whether the last call to Alert Manager was successful.").register();

	private List<AlertDto> alerts = Collections.emptyList();

	private Instant lastUpdateTime = Instant.EPOCH;
	private final Optional<String> url = Optional.ofNullable(System.getenv("ALERTMANAGER_URL"))
			.map(url -> String.format("%s/api/v1/alerts", url));

	@Override
	public boolean isOverloaded(String environment, Set<String> services) {
		return url.<Boolean>map(address -> {
			if (Duration.between(lastUpdateTime, Instant.now()).get(ChronoUnit.MINUTES) > 5) {
				try (CloseableHttpResponse response = HTTP_CLIENT.execute(new HttpGet(address))) {
					final AlertResultDto result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
							AlertResultDto.class);
					if (result == null || result.getData() == null) {
						return false;
					}
					alerts = result.getData();
					ok.set(1);
					lastUpdateTime = Instant.now();
					lastUpdated.setToCurrentTime();
				} catch (final IOException e) {
					e.printStackTrace();
					ok.set(0);
				}
			}
			return alerts.stream().anyMatch(alert -> alert.matches(environment, services));
		}).orElse(false);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return url.map(address -> {
			final Map<String, String> properties = new TreeMap<>();
			properties.put("address", address);
			return Stream.of(new Pair<>("Prometheus Throttler", properties));
		}).orElseGet(Stream::empty);
	}

}
