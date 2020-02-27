package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import io.prometheus.client.Gauge;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class WorkflowFile implements WatchedFileListener {
  private static final Gauge badFile =
      Gauge.build("shesmu_niassa_worflow_bad", "The Niassa workflow file failed to be parsed.")
          .labelNames("filename")
          .register();
  private WorkflowConfiguration configuration;
  private final Path path;

  public WorkflowFile(Path path) {
    this.path = path;
  }

  @Override
  public void start() {
    // Do nothing
  }

  @Override
  public void stop() {
    // Do nothing
  }

  public Stream<Pair<String, WorkflowConfiguration>> stream() {
    return configuration == null
        ? Stream.empty()
        : Stream.of(
            new Pair<>(
                path.getFileName().getFileName().toString().replace(".niassawf", ""),
                configuration));
  }

  @Override
  public Optional<Integer> update() {
    try (final InputStream input = new FileInputStream(path.toFile())) {
      configuration = NiassaServer.MAPPER.readValue(input, WorkflowConfiguration.class);
      badFile.labels(path.toString()).set(0);
    } catch (IOException e) {
      e.printStackTrace();
      badFile.labels(path.toString()).set(1);
    }
    return Optional.empty();
  }
}
