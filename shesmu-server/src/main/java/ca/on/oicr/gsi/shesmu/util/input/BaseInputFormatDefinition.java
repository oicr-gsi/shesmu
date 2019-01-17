package ca.on.oicr.gsi.shesmu.util.input;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputRepository;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Create a new {@link InputFormatDefinition} that uses Java service loaders to find implementation
 * sources of input data and annotations to generate Shesmu variables
 *
 * @param <V> the type for each “row”, decorated with {@link ShesmuVariable} annotations
 * @param <R> the type of the interface implemented by sources of input data
 */
public abstract class BaseInputFormatDefinition<V, R extends InputRepository<V>>
    extends InputFormatDefinition {

  private final Class<V> itemClass;
  private final ServiceLoader<R> loader;
  private final Target[] variables;

  private final Class<Consumer<V>> writer;

  public BaseInputFormatDefinition(String name, Class<V> itemClass, Class<R> repositoryClass) {
    super(name);
    this.itemClass = itemClass;
    loader = ServiceLoader.load(repositoryClass);
    variables = TargetUtils.readAnnotationsFor(itemClass);
    writer = new JsonWriterCompiler().create(Stream.of(variables), itemClass);
  }

  @Override
  public final Stream<Target> baseStreamVariables() {
    return Arrays.stream(variables);
  }

  @Override
  public final Stream<LoadedConfiguration> configuration() {
    return StreamSupport.stream(loader.spliterator(), false).map(x -> x);
  }

  @Override
  public final <T> Stream<T> input(Class<T> clazz) {
    if (clazz.isAssignableFrom(itemClass)) {
      return stream().map(clazz::cast);
    }
    return Stream.empty();
  }

  @Override
  public final Class<?> itemClass() {
    return itemClass;
  }

  private Stream<V> stream() {
    return StreamSupport.stream(loader.spliterator(), false).flatMap(InputRepository::stream);
  }

  @Override
  public void write(JsonGenerator generator) throws IOException {
    try {
      stream().forEach(writer.getConstructor(JsonGenerator.class).newInstance(generator));
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException e) {
      e.printStackTrace();
    }
  }
}
