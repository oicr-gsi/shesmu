package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class MongoServer extends JsonPluginFile<Configuration> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Optional<Configuration> configuration = Optional.empty();
  private Optional<MongoClient> connection = Optional.empty();
  private final Definer<MongoServer> definer;

  public MongoServer(Path fileName, String instanceName, Definer<MongoServer> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public synchronized void configuration(SectionRenderer renderer) {
    renderer.line("Filename", fileName().toString());
    renderer.line("Has Configuration?", configuration.isPresent() ? "Yes" : "No");
  }

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    connection = Optional.of(MongoClients.create(configuration.getUri()));
    definer.clearFunctions();
    for (final var entry : configuration.getFunctions().entrySet()) {
      final var function = entry.getValue();
      definer.defineFunction(
          entry.getKey(),
          function.getDescription(),
          function.getSelector().type(function.getResultType().type()),
          new VariadicFunction() {
            private final Definer<MongoServer> definer = MongoServer.this.definer;
            private final KeyValueCache<Tuple, Optional<Object>> cache =
                new KeyValueCache<>(
                    String.format("mongo %s %s", MongoServer.this.fileName(), entry.getKey()),
                    function.getTtl(),
                    SimpleRecord::new) {
                  @Override
                  protected Optional<Object> fetch(Tuple key, Instant lastUpdated) {
                    return Optional.of(function.apply(definer.get().connection.get(), key));
                  }
                };

            @Override
            public Object apply(Object... arguments) {
              return cache
                  .get(new Tuple(arguments))
                  .orElseThrow(
                      () -> new IllegalStateException("Failed to get response for Mongo request"));
            }
          },
          function.getParameters().stream()
              .map(p -> new FunctionParameter("Mongo function parameter", p.type()))
              .toArray(FunctionParameter[]::new));
    }
    return Optional.empty();
  }
}
