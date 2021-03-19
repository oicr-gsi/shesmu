package ca.on.oicr.gsi.shesmu.redict;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Definer.RefillDefiner;
import ca.on.oicr.gsi.shesmu.plugin.Definer.RefillInfo;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefillableDictionary extends JsonPluginFile<Configuration> {

  public static final class DictionaryRefiller<I> extends Refiller<I> {

    private final AtomicReference<Map<Object, Object>> dictionary;
    private Function<I, Object> key;
    private Function<I, Object> value;

    public DictionaryRefiller(AtomicReference<Map<Object, Object>> dictionary) {
      super();
      this.dictionary = dictionary;
    }

    @Override
    public void consume(Stream<I> items) {
      dictionary.set(items.collect(Collectors.toMap(key, value, (a, b) -> a)));
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Definer<RefillableDictionary> definer;

  public RefillableDictionary(
      Definer<RefillableDictionary> definer, Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    // Do nothing
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    if (configuration.getKey().isBad() || configuration.getValue().isBad()) {
      return Optional.empty();
    }

    final var dictionary = new AtomicReference<>(Map.of());
    definer.defineConstantBySupplier(
        "get",
        "Dictionary of values collected from refiller.",
        Imyhat.dictionary(configuration.getKey(), configuration.getValue()),
        dictionary::get);
    definer.defineRefiller(
        "put",
        "A way to fill the dictionary available as a constant of the same name",
        new RefillDefiner() {
          @Override
          public <I> RefillInfo<I, DictionaryRefiller<I>> info(Class<I> rowType) {
            return new RefillInfo<>() {
              @Override
              public DictionaryRefiller<I> create() {
                return new DictionaryRefiller<>(dictionary);
              }

              @Override
              public Stream<CustomRefillerParameter<DictionaryRefiller<I>, I>> parameters() {
                return Stream.of(
                    new CustomRefillerParameter<>("key", configuration.getKey()) {
                      @Override
                      public void store(
                          DictionaryRefiller<I> refiller, Function<I, Object> function) {
                        refiller.key = function;
                      }
                    },
                    new CustomRefillerParameter<>("value", configuration.getValue()) {
                      @Override
                      public void store(
                          DictionaryRefiller<I> refiller, Function<I, Object> function) {
                        refiller.value = function;
                      }
                    });
              }

              @Override
              public Class<? extends Refiller> type() {
                return DictionaryRefiller.class;
              }
            };
          }
        });
    return Optional.empty();
  }
}
