package ca.on.oicr.gsi.shesmu.stdout;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class StdoutPlugin extends JsonPluginFile<Configuration> {
  private final Definer<StdoutPlugin> definer;
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, true)
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .build();
  private Optional<Configuration> configuration = Optional.empty();

  public StdoutPlugin(Path fileName, String instanceName, Definer<StdoutPlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    configuration.ifPresent(value -> renderer.line("Level", value.level().toString()));
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    this.configuration = Optional.of(value);
    return Optional.empty();
  }

  @Override
  public synchronized void writeLog(
      String message, LogLevel level, Map<String, String> attributes) {
    if (level.compareTo(this.configuration.get().level()) >= 0) {
      StringBuilder writeOut = new StringBuilder();
      writeOut.append(message);
      for (Map.Entry<String, String> e : attributes.entrySet()) {
        writeOut.append(", ").append(e.getKey()).append(": ").append(e.getValue());
      }
      System.out.println(writeOut);
    }
  }
}
