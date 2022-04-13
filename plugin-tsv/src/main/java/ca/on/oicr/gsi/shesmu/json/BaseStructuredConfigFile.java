package ca.on.oicr.gsi.shesmu.json;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseStructuredConfigFile<T extends BaseConfiguration>
    extends JsonPluginFile<T> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge badEntry =
      Gauge.build(
              "shesmu_structured_config_bad_entry",
              "Whether a particular entry in a structure configuration file is bad")
          .labelNames("filename", "entry")
          .register();
  private final Definer<? extends BaseStructuredConfigFile<T>> definer;
  private Set<String> badRecords = Set.of();

  public BaseStructuredConfigFile(
      Class<T> clazz,
      Path fileName,
      String instanceName,
      Definer<? extends BaseStructuredConfigFile<T>> definer) {
    super(fileName, instanceName, MAPPER, clazz);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    for (final var badRecord : badRecords) {
      renderer.line("Bad record", badRecord);
    }
  }

  protected abstract Optional<Integer> ttlOnSuccess(T configuration);

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  protected final Optional<Integer> update(T configuration) {
    return values(configuration)
        .map(
            input -> {
              final var type =
                  new Imyhat.ObjectImyhat(
                      configuration.getTypes().entrySet().stream()
                          .map(e -> new Pair<>(e.getKey(), e.getValue())));
              final Set<String> badRecords = new TreeSet<>();
              final Map<String, Optional<Tuple>> values =
                  input.collect(
                      Collectors.toMap(
                          Map.Entry::getKey,
                          e -> {
                            final var ok = new AtomicBoolean(true);
                            final var convertedValues =
                                type.fields()
                                    .sorted(
                                        Comparator.comparing(field -> field.getValue().second()))
                                    .map(
                                        field -> {
                                          try {

                                            return field
                                                .getValue()
                                                .first()
                                                .apply(
                                                    new UnpackJson(
                                                        e.getValue()
                                                            .getOrDefault(
                                                                field.getKey(),
                                                                configuration
                                                                    .getDefaults()
                                                                    .getOrDefault(
                                                                        field.getKey(),
                                                                        NullNode.getInstance()))));
                                          } catch (Exception ex) {
                                            ex.printStackTrace();
                                            ok.set(false);
                                            return null;
                                          }
                                        })
                                    .toArray();
                            badEntry
                                .labels(fileName().toString(), e.getKey())
                                .set(ok.get() ? 0 : 1);
                            return ok.get()
                                ? Optional.of(new Tuple(convertedValues))
                                : Optional.empty();
                          }));
              final Optional<Tuple> missingResult;
              if (configuration.isMissingUsesDefaults()) {
                final Set<String> missingFields = new HashSet<>();
                final var convertedValues =
                    type.fields()
                        .sorted(Comparator.comparing(field -> field.getValue().second()))
                        .map(
                            field -> {
                              try {
                                return field
                                    .getValue()
                                    .first()
                                    .apply(
                                        new UnpackJson(
                                            configuration
                                                .getDefaults()
                                                .getOrDefault(
                                                    field.getKey(), NullNode.getInstance())));
                              } catch (Exception ex) {
                                ex.printStackTrace();
                                missingFields.add(field.getKey());
                                return null;
                              }
                            })
                        .toArray();
                if (missingFields.isEmpty()) {
                  missingResult = Optional.of(new Tuple(convertedValues));
                } else {
                  throw new IllegalArgumentException(
                      "Default requires all fields, but missing: "
                          + String.join(", ", missingFields));
                }
              } else {
                missingResult = Optional.empty();
              }
              this.badRecords = badRecords;
              definer.clearFunctions();
              definer.defineFunction(
                  "get",
                  "JSON configuration from " + fileName(),
                  type.asOptional(),
                  args -> values.getOrDefault(args[0], missingResult),
                  new FunctionParameter("Lookup key", Imyhat.STRING));
              definer.defineFunction(
                  "has",
                  "JSON configuration from " + fileName(),
                  ReturnTypeGuarantee.BOOLEAN,
                  "Lookup key",
                  TypeGuarantee.STRING,
                  values::containsKey);
              return ttlOnSuccess(configuration);
            })
        .orElse(Optional.of(5));
  }

  protected abstract Optional<Stream<Map.Entry<String, Map<String, JsonNode>>>> values(
      T configuration);
}
