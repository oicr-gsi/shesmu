package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.util.LoadedConfiguration;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class StaticActions implements LoadedConfiguration {
  private class StaticActionFile implements WatchedFileListener {
    private final Path fileName;
    int lastCount;

    public StaticActionFile(Path fileName) {
      this.fileName = fileName;
    }

    public Path fileName() {
      return fileName;
    }

    @Override
    public void start() {
      // Do nothing.
    }

    @Override
    public void stop() {
      // Reset counts for this file
      processedCount.labels(fileName().toString()).set(0);
      totalCount.labels(fileName().toString()).set(0);
    }

    @Override
    public Optional<Integer> update() {
      try {
        var actions = MAPPER.readValue(fileName().toFile(), StaticAction[].class);
        lastCount = actions.length;
        var success = 0;
        var retry = false;
        final var hash = Integer.toHexString(Arrays.hashCode(actions));
        for (final var action : actions) {
          if (!runners.containsKey(action.getName())) {
            final var runner =
                repository
                    .actions()
                    .filter(definition -> definition.name().equals(action.getName()))
                    .findFirst()
                    .map(ActionRunnerCompiler::new)
                    .map(ActionRunnerCompiler::compile)
                    .filter(Objects::nonNull);

            runner.ifPresent(d -> runners.put(action.getName(), d));
            if (runner.isEmpty()) {
              retry = true;
              continue;
            }
          }
          try {
            final var result = runners.get(action.getName()).run(action.getParameters());
            if (result != null) {
              result.prepare();
              sink.accept(
                  result,
                  fileName().toString(),
                  1,
                  1,
                  hash,
                  action.getTags().toArray(String[]::new));
              success++;
            } else {
              retry = true;
            }
          } catch (final Exception e) {
            e.printStackTrace();
            retry = true;
          }
        }
        totalCount.labels(fileName().toString()).set(lastCount);
        processedCount.labels(fileName().toString()).set(success);
        return retry ? Optional.of(15) : Optional.empty();
      } catch (Exception e) {
        e.printStackTrace();
        return Optional.empty();
      }
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Gauge processedCount =
      Gauge.build(
              "shesmu_static_actions_processed_count",
              "The number of static actions defined in a file that were succsessfully added to the actions queue.")
          .labelNames("filename")
          .register();

  private static final Gauge totalCount =
      Gauge.build(
              "shesmu_static_actions_total_count",
              "The number of static actions defined in a file.")
          .labelNames("filename")
          .register();

  private AutoUpdatingDirectory<StaticActionFile> configuration;

  private final DefinitionRepository repository;
  private final Map<String, ActionRunner> runners = new HashMap<>();
  private final OliveServices sink;

  public StaticActions(OliveServices sink, DefinitionRepository repository) {
    this.sink = sink;
    this.repository = repository;
  }

  @Override
  public Stream<ConfigurationSection> listConfiguration() {
    return Stream.of(
        new ConfigurationSection("Static Configuration") {

          @Override
          public void emit(SectionRenderer renderer) {
            if (configuration != null) {
              configuration.stream()
                  .sorted(Comparator.comparing(StaticActionFile::fileName))
                  .forEach(config -> renderer.line(config.fileName().toString(), config.lastCount));
            }
          }
        });
  }

  public void start(FileWatcher fileWatcher) {
    if (configuration == null) {
      configuration = new AutoUpdatingDirectory<>(fileWatcher, ".actnow", StaticActionFile::new);
    }
  }
}
