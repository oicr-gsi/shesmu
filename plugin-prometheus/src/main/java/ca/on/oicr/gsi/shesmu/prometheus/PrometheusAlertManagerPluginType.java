package ca.on.oicr.gsi.shesmu.prometheus;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

/**
 * Determines if throttling should occur based on a Prometheus Alert Manager
 *
 * <p>It assumes that there will be an alert firing:
 *
 * <pre>
 * AutoInhibit{environment="e",job="s"}
 * </pre>
 *
 * where <i>e</i> matches the environment specified in the configuration file (or is absent) and
 * <i>s</i> is one of the services specified by the action.
 */
@MetaInfServices
public class PrometheusAlertManagerPluginType
    extends PluginFileType<PrometheusAlertManagerPluginType.AlertManagerEndpoint> {
  private static final Gauge pushOk =
      Gauge.build(
              "shesmu_alert_push_alertman_good",
              "Whether the last push of alerts to Alert Manager was successful.")
          .labelNames("target")
          .register();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static class AlertManagerEndpoint extends JsonPluginFile<Configuration> {
    private class AlertCache extends ValueCache<Stream<AlertDto>, Stream<AlertDto>> {
      public AlertCache(Path fileName) {
        super("alertmanager " + fileName.toString(), 5, ReplacingRecord::new);
      }

      @Override
      protected Stream<AlertDto> fetch(Instant lastUpdated) throws Exception {
        final var url = configuration.map(Configuration::getAlertmanager).orElse(null);
        if (url == null) {
          return Stream.empty();
        }
        try (var response =
            HTTP_CLIENT.execute(new HttpGet(String.format("%s/api/v1/alerts", url)))) {
          final var result =
              MAPPER.readValue(response.getEntity().getContent(), AlertResultDto.class);
          if (result == null || result.getData() == null) {
            return Stream.empty();
          }
          return result.getData().stream();
        }
      }
    }

    private final AlertCache cache;
    private Optional<Configuration> configuration = Optional.empty();

    public AlertManagerEndpoint(Path fileName, String instanceName) {
      super(fileName, instanceName, MAPPER, Configuration.class);
      cache = new AlertCache(fileName);
    }

    public void configuration(SectionRenderer renderer) {
      configuration.ifPresent(
          c -> {
            renderer.link("Address", c.getAlertmanager(), c.getAlertmanager());
            renderer.line("Environment", c.getEnvironment());
            for (final var label : c.getLabels()) {
              renderer.line("Inhibition Alert Label", label);
            }
          });
    }

    @Override
    public void pushAlerts(String alertJson) {
      configuration.ifPresent(
          config -> {
            var request = new HttpPost(String.format("%s/api/v1/alerts", config.getAlertmanager()));
            request.addHeader("Content-type", ContentType.APPLICATION_JSON.getMimeType());
            request.setEntity(new StringEntity(alertJson, StandardCharsets.UTF_8));
            try (var response = HTTP_CLIENT.execute(request)) {
              var ok = response.getStatusLine().getStatusCode() != 200;
              if (ok) {
                System.err.printf(
                    "Failed to write alerts to %s: %d %s",
                    config.getAlertmanager(),
                    response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase());
              }
              pushOk.labels(config.getAlertmanager()).set(ok ? 1 : 0);
            } catch (IOException e) {
              e.printStackTrace();
              pushOk.labels(config.getAlertmanager()).set(0);
            }
          });
    }

    @Override
    public Stream<String> isOverloaded(Set<String> services) {
      final var environment = configuration.map(Configuration::getEnvironment).orElse("");
      final var labels = configuration.map(Configuration::getLabels).orElse(List.of("job"));
      final var knownOverloads =
          cache
              .get()
              .flatMap(alert -> alert.matches(environment, labels))
              .collect(Collectors.toSet());
      return services.stream().filter(knownOverloads::contains);
    }

    @Override
    protected Optional<Integer> update(Configuration value) {
      configuration = Optional.of(value);
      cache.invalidate();
      return Optional.empty();
    }
  }

  private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

  public PrometheusAlertManagerPluginType() {
    super(MethodHandles.lookup(), AlertManagerEndpoint.class, ".alertman", "prometheus");
  }

  @Override
  public AlertManagerEndpoint create(
      Path filePath, String instanceName, Definer<AlertManagerEndpoint> definer) {
    return new AlertManagerEndpoint(filePath, instanceName);
  }
}
