package ca.on.oicr.gsi.shesmu.util.input;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Create a new {@link InputFormatDefinition} that uses an existing input format for the data
 *
 * <p>This wraps existing input data into a new format. This is useful when changing a data format.
 * Create a wrapper type that looks like the old input format backed by the new input format then
 * create a decorator input format to bridge them.
 *
 * @param <I> the real (input) type for each “row” to be consumed
 * @param <O> the decorator (output) type for each “row”, with {@link ShesmuVariable} annotations
 */
public abstract class DecoratedInputFormatDefinition<I, O> extends InputFormatDefinition {

  private final Class<I> inputClass;
  private final Class<O> outputClass;
  private final Target[] variables;
  private final Class<Consumer<O>> writer;

  public DecoratedInputFormatDefinition(String name, Class<I> inputClass, Class<O> outputClass) {
    super(name);
    this.inputClass = inputClass;
    this.outputClass = outputClass;
    variables = TargetUtils.readAnnotationsFor(outputClass);
    writer = new JsonWriterCompiler().create(Stream.of(variables), outputClass);
  }

  @Override
  public final Stream<Target> baseStreamVariables() {
    return Arrays.stream(variables);
  }

  @Override
  public final Stream<LoadedConfiguration> configuration() {
    return Stream.empty();
  }

  @Override
  public final <T> Stream<T> input(Class<T> clazz) {
    if (clazz.isAssignableFrom(outputClass)) {
      return stream().map(clazz::cast);
    }
    return Stream.empty();
  }

  @Override
  public final Class<?> itemClass() {
    return outputClass;
  }

  private Stream<O> stream() {
    return InputFormatDefinition.all(inputClass).map(this::wrap);
  }

  protected abstract O wrap(I input);

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
