package ca.on.oicr.gsi.shesmu.loki;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class LokiPlugin extends JsonPluginFile<Configuration> {

  private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
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
  private final Map<Map<String, String>, List<Pair<Instant, String>>> buffer = new HashMap<>();
  private Optional<Configuration> configuration = Optional.empty();
  private final Definer<LokiPlugin> definer;

  public LokiPlugin(Path fileName, String instanceName, Definer<LokiPlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    configuration.ifPresent(
        configuration -> {
          renderer.link("Instance", configuration.getUrl(), configuration.getUrl());
          for (final Map.Entry<String, String> entry : configuration.getLabels().entrySet()) {
            renderer.line("Label: " + entry.getKey(), entry.getValue());
          }
        });
  }

  private void flush(Instant now) {
    configuration.ifPresent(
        c -> {
          int size = 0;
          boolean flush = false;
          for (final List<Pair<Instant, String>> list : buffer.values()) {
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
            final ObjectNode body = MAPPER.createObjectNode();
            final ArrayNode streams = body.putArray("streams");
            for (final Entry<Map<String, String>, List<Pair<Instant, String>>> entry :
                buffer.entrySet()) {
              final ObjectNode stream = streams.addObject();
              final ObjectNode labels = stream.putObject("stream");
              for (final Entry<String, String> label : entry.getKey().entrySet()) {
                labels.put(label.getKey(), label.getValue());
              }
              for (final Entry<String, String> label : c.getLabels().entrySet()) {
                labels.put(label.getKey(), label.getValue());
              }
              final ArrayNode values = stream.putArray("values");
              entry.getValue().sort(Comparator.comparing(Pair::first));
              for (final Pair<Instant, String> value : entry.getValue()) {
                final ArrayNode record = values.addArray();
                record.add(
                    String.format(
                        "%d%09d", value.first().getEpochSecond(), value.first().getNano()));
                record.add(value.second());
              }
            }
            final HttpPost request = new HttpPost(c.getUrl());
            try {
              request.setEntity(
                  new StringEntity(MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));
            } catch (final Exception e) {
              e.printStackTrace();
              error.labels(fileName().toString()).set(1);
            }
            writeTime.labels(fileName().toString()).setToCurrentTime();
            try (AutoCloseable timer = writeLatency.start(fileName().toString());
                CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
              final boolean success = response.getStatusLine().getStatusCode() / 100 == 2;
              error.labels(fileName().toString()).set(success ? 0 : 1);
              if (success) {
                buffer.clear();
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
  public synchronized void writeLog(String message, Map<String, String> attributes) {
    final Instant now = Instant.now();
    buffer.computeIfAbsent(attributes, k -> new ArrayList<>()).add(new Pair<>(now, message));
    flush(now);
  }
}
