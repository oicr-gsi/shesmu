package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Creates a watched JSON file that will be notified when the file changes on disk and parsed */
public abstract class JsonPluginFile<T> extends PluginFile {
  public static final Gauge GOOD_JSON =
      Gauge.build("shesmu_auto_update_good_json", "Whether a JSON configuration file is valid.")
          .labelNames("filename")
          .register();

  private final Class<T> clazz;

  private final ObjectMapper mapper;

  /**
   * Creates a new monitor
   *
   * @param fileName the file to monitor
   * @param clazz the class to parse the JSON file as
   */
  public JsonPluginFile(Path fileName, String instanceName, ObjectMapper mapper, Class<T> clazz) {
    super(fileName, instanceName);
    this.mapper = mapper;
    this.clazz = clazz;
  }

  @Override
  public final Optional<Integer> update() {
    try {
      final var contents = Files.readAllBytes(fileName());
      if (contents.length == 0) {
        // When a file is being checked out by git, it often shows up as empty. Rather than
        // complaining about it, wait a minute.
        return Optional.of(1);
      }
      final var value = mapper.readValue(contents, clazz);
      GOOD_JSON.labels(fileName().toString()).set(1);
      return update(value);
    } catch (final Exception e) {
      e.printStackTrace();
      GOOD_JSON.labels(fileName().toString()).set(0);
      return Optional.empty();
    }
  }

  /**
   * Called when the underlying file has been parsed
   *
   * @param value the parsed file contents
   */
  protected abstract Optional<Integer> update(T value);
}
