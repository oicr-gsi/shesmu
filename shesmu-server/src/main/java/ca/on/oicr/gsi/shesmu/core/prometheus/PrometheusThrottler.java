package ca.on.oicr.gsi.shesmu.core.prometheus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

/**
 * Determines if throttling should occur based on a Prometheus Alert Manager
 *
 * It assumes that there will be an alert firing:
 *
 * <pre>
 * AutoInhibit{environment="e",job="s"}
 * </pre>
 *
 * where <i>e</i> matches the environment specified in the configuration file
 * (or is absent) and <i>s</i> is one of the services specified by the action.
 */
@MetaInfServices(Throttler.class)
public class PrometheusThrottler implements Throttler {
	private class Endpoint extends AutoUpdatingJsonFile<Configuration> {

		private Optional<Configuration> configuration = Optional.empty();

		public Endpoint(Path fileName) {
			super(fileName, Configuration.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("Prometheus Throttler: " + fileName().toString()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					configuration.ifPresent(c -> {
						renderer.link("Address", c.getAlertmanager(), c.getAlertmanager());
						renderer.line("Environment", c.getEnvironment());
					});

				}
			};
		}

		public boolean isOverloaded(Set<String> services) {
			return configuration.flatMap(configuration -> //
			CACHE.get(configuration.getAlertmanager())//
					.map(l -> l.stream()//
							.anyMatch(alert -> alert.matches(configuration.getEnvironment(), services))))
					.orElse(false);
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			configuration = Optional.of(value);
			return Optional.empty();
		}

	}

	private static Cache<String, List<AlertDto>> CACHE = new Cache<String, List<AlertDto>>("alertmanager", 5) {

		@Override
		protected List<AlertDto> fetch(String key) throws IOException {
			try (CloseableHttpResponse response = HTTP_CLIENT
					.execute(new HttpGet(String.format("%s/api/v1/alerts", key)))) {
				final AlertResultDto result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
						AlertResultDto.class);
				if (result == null || result.getData() == null) {
					return null;
				}
				return result.getData();
			}
		}

	};

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final AutoUpdatingDirectory<Endpoint> configuration = new AutoUpdatingDirectory<>(".alertman",
			Endpoint::new);

	@Override
	public boolean isOverloaded(Set<String> services) {
		return configuration.stream()//
				.anyMatch(ep -> ep.isOverloaded(services));
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return configuration.stream().map(Endpoint::configuration);
	}

}
