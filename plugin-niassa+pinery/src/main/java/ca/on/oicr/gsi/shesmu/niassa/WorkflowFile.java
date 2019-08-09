package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class WorkflowFile implements WatchedFileListener {
  private final Path path;
  private WorkflowConfiguration configuration;

  public WorkflowFile(Path path) {
    this.path = path;
  }

  @Override
  public void start() {
    update();
  }

  @Override
  public void stop() {
    // Do nothing
  }

  @Override
  public Optional<Integer> update() {
    try (final InputStream input = new FileInputStream(path.toFile())) {
      configuration = NiassaServer.MAPPER.readValue(input, WorkflowConfiguration.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Stream<Pair<String, WorkflowConfiguration>> stream() {
    return configuration == null
        ? Stream.empty()
        : Stream.of(
            new Pair<>(
                path.getFileName().getFileName().toString().replace(".niassawf", ""),
                configuration));
  }
}
