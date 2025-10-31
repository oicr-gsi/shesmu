package ca.on.oicr.gsi.shesmu.prometheus;

import static ca.on.oicr.gsi.shesmu.plugin.Utils.httpGet;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private class AlertCache extends ValueCache<Stream<AlertDto>> {
      public AlertCache(Path fileName) {
        super("alertmanager " + fileName.toString(), 5, ReplacingRecord::new);
      }

      @Override
      protected Stream<AlertDto> fetch(Instant lastUpdated) throws Exception {
        final var url = configuration.map(Configuration::getAlertmanager).orElse(null);
        if (url == null) {
          return Stream.empty();
        }
        var response =
            HTTP_CLIENT.send(
                httpGet(
                    String.format("%s/api/v1/alerts", url),
                    configuration.map(Configuration::getTimeout)),
                new JsonBodyHandler<>(MAPPER, AlertResultDto.class));
        final var result = response.body().get();
        if (result == null || result.getData() == null) {
          return Stream.empty();
        }
        return result.getData().stream();
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
            var request =
                HttpRequest.newBuilder(
                        URI.create(String.format("%s/api/v1/alerts", config.getAlertmanager())))
                    .header("Content-type", "application/json")
                    .timeout(Duration.ofMinutes(config.getTimeout()))
                    .POST(BodyPublishers.ofString(alertJson, StandardCharsets.UTF_8))
                    .build();
            try {
              final var response = HTTP_CLIENT.send(request, BodyHandlers.discarding());
              var ok = response.statusCode() != 200;
              if (ok) {
                System.err.printf(
                    "Failed to write alerts to %s: %d",
                    config.getAlertmanager(), response.statusCode());
              }
              pushOk.labels(config.getAlertmanager()).set(ok ? 1 : 0);
            } catch (Exception e) {
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

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  public PrometheusAlertManagerPluginType() {
    super(MethodHandles.lookup(), AlertManagerEndpoint.class, ".alertman", "prometheus");
  }

  @Override
  public AlertManagerEndpoint create(
      Path filePath, String instanceName, Definer<AlertManagerEndpoint> definer) {
    return new AlertManagerEndpoint(filePath, instanceName);
  }
}
