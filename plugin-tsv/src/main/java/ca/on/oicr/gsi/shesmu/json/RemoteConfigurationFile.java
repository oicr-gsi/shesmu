package ca.on.oicr.gsi.shesmu.json;

import static ca.on.oicr.gsi.shesmu.plugin.Utils.httpGet;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class RemoteConfigurationFile extends BaseStructuredConfigFile<RemoteConfiguration> {
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public RemoteConfigurationFile(
      Path filePath, String instanceName, Definer<RemoteConfigurationFile> definer) {
    super(RemoteConfiguration.class, filePath, instanceName, definer);
  }

  @Override
  protected Optional<Integer> ttlOnSuccess(RemoteConfiguration configuration) {
    return Optional.of(configuration.getTtl());
  }

  @Override
  protected Optional<Stream<Map.Entry<String, Map<String, JsonNode>>>> values(
      RemoteConfiguration configuration) {
    try {
      return Optional.of(
          HTTP_CLIENT
              .send(
                  httpGet(configuration.getUrl(), Optional.of(configuration.getTimeout())),
                  new JsonBodyHandler<>(
                      MAPPER, new TypeReference<Map<String, Map<String, JsonNode>>>() {}))
              .body()
              .get()
              .entrySet()
              .stream());
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }
}
