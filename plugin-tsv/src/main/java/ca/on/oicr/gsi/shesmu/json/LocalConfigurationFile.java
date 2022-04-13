package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class LocalConfigurationFile extends BaseStructuredConfigFile<LocalConfiguration> {
  public LocalConfigurationFile(
      Path filePath, String instanceName, Definer<LocalConfigurationFile> definer) {
    super(LocalConfiguration.class, filePath, instanceName, definer);
  }

  @Override
  protected Optional<Integer> ttlOnSuccess(LocalConfiguration configuration) {
    return Optional.empty();
  }

  @Override
  protected Optional<Stream<Map.Entry<String, Map<String, JsonNode>>>> values(
      LocalConfiguration configuration) {
    return Optional.of(configuration.getValues().entrySet().stream());
  }
}
