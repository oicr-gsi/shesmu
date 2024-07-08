package ca.on.oicr.gsi.shesmu.loki;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;

public class LokiPlugin extends JsonPluginFile<Configuration> {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge error =
      Gauge.build(
              "shesmu_loki_push_error",
              "Whether the Loki client had a push error on its last write")
          .labelNames("filename")
          .register();
  private static final LatencyHistogram writeLatency =
      new LatencyHistogram(
          "shesmu_loki_write_latency",
          "The amount of time to took to push data to Loki",
          "filename");
  private static final Gauge writeTime =
      Gauge.build("shesmu_loki_write_time", "The last time Shesmu attempted to pushed data to Loki")
          .labelNames("filename")
          .register();
  private final Pattern INVALID_LABEL = Pattern.compile("[^a-zA-Z0-9_]");
  private final Map<Map<String, String>, List<Pair<Instant, String>>> buffer = new HashMap<>();
  private Optional<Configuration> configuration = Optional.empty();
  private final Definer<LokiPlugin> definer;

  public LokiPlugin(Path fileName, String instanceName, Definer<LokiPlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    configuration.ifPresent(
        configuration -> {
          renderer.link("Instance", configuration.getUrl(), configuration.getUrl());
          renderer.line("Level", configuration.getLevel().toString());
          for (final var entry : configuration.getLabels().entrySet()) {
            renderer.line("Label: " + entry.getKey(), entry.getValue());
          }
        });
  }

  private void flush(Instant now) {
    configuration.ifPresent(
        c -> {
          var size = 0;
          var flush = false;
          for (final var list : buffer.values()) {
            size += list.size();
            flush =
                list.stream()
                    .map(Pair::first)
                    .min(Comparator.naturalOrder())
                    .map(x -> Duration.between(x, now).getSeconds() > 60)
                    .orElse(false);
            if (flush || size > 10) {
              flush = true;
              break;
            }
          }
          if (flush) {
            final var body = MAPPER.createObjectNode();
            final var streams = body.putArray("streams");
            for (final var entry : buffer.entrySet()) {
              final var stream = streams.addObject();
              final var labels = stream.putObject("stream");
              for (final var label : entry.getKey().entrySet()) {
                labels.put(INVALID_LABEL.matcher(label.getKey()).replaceAll("_"), label.getValue());
              }
              for (final var label : c.getLabels().entrySet()) {
                labels.put(INVALID_LABEL.matcher(label.getKey()).replaceAll("_"), label.getValue());
              }
              final var values = stream.putArray("values");
              entry.getValue().sort(Comparator.comparing(Pair::first));
              for (final var value : entry.getValue()) {
                final var record = values.addArray();
                record.add(
                    String.format(
                        "%d%09d", value.first().getEpochSecond(), value.first().getNano()));
                record.add(value.second().replace('\n', ' '));
              }
            }
            final HttpRequest request;
            try {
              // This doesn't use the built-in constant for JSON because that one includes a charset
              // and Loki then thinks the request is a protobuf
              request =
                  HttpRequest.newBuilder(URI.create(c.getUrl()))
                      .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                      .header("Content-type", "application/json")
                      .build();
            } catch (final Exception e) {
              e.printStackTrace();
              error.labels(fileName().toString()).set(1);
              return;
            }
            writeTime.labels(fileName().toString()).setToCurrentTime();
            try (final var timer = writeLatency.start(fileName().toString())) {
              final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
              final var success = response.statusCode() / 100 == 2;
              if (success) {
                buffer.clear();
                error.labels(fileName().toString()).set(0);
              } else {
                try (final var s = new Scanner(response.body())) {
                  s.useDelimiter("\\A");
                  if (s.hasNext()) {
                    final var message = s.next();
                    if (message.contains("ignored")) {
                      buffer.clear();
                      // Loki complains if we send duplicate messages, so treat that like success
                      error.labels(fileName().toString()).set(0);
                      return;
                    }
                    System.err.println(message);
                  }
                }
                error.labels(fileName().toString()).set(1);
              }
            } catch (final Exception e) {
              e.printStackTrace();
              error.labels(fileName().toString()).set(1);
            }
          }
        });
  }

  @Override
  protected synchronized Optional<Integer> update(Configuration configuration) {
    // Flush logs to the old configuration if it's about to change; if there's a backlog, it will
    // get flushed in a minute.
    flush(Instant.now());
    this.configuration = Optional.of(configuration);
    // Keep waking us up to flush logs
    return Optional.of(1);
  }

  @Override
  public synchronized void writeLog(
      String message, LogLevel level, Map<String, String> attributes) {
    final var now = Instant.now();
    if (level.compareTo(this.configuration.get().getLevel()) >= 0)
      buffer.computeIfAbsent(attributes, k -> new ArrayList<>()).add(new Pair<>(now, message));
    flush(now);
  }
}
