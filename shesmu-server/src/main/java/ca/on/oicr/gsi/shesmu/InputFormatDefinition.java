package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.objectweb.asm.Type;

/** Define a <tt>Input</tt> format for olives to consume */
public abstract class InputFormatDefinition {
  private static final ServiceLoader<InputFormatDefinition> LOADER =
      ServiceLoader.load(InputFormatDefinition.class);

  /**
   * Get all the data available for a particular format
   *
   * @param clazz the format requested.
   * @return
   */
  public static <T> Stream<T> all(Class<T> clazz) {
    return formats().flatMap(s -> s.input(clazz));
  }

  /** Get all the configuration for all formats */
  public static Stream<LoadedConfiguration> allConfiguration() {
    return formats().flatMap(InputFormatDefinition::configuration);
  }

  /** List all supported formats */
  public static Stream<InputFormatDefinition> formats() {
    return StreamSupport.stream(LOADER.spliterator(), false);
  }

  private final String name;

  public InputFormatDefinition(String name) {
    this.name = name;
  }

  /**
   * Get all the variables available for this format
   *
   * @return
   */
  public abstract Stream<Target> baseStreamVariables();

  /** Get the configuration for all the repositories for this format. */
  public abstract Stream<LoadedConfiguration> configuration();

  /**
   * Get all information currently available for this format
   *
   * @param clazz the format requested
   * @return a stream of available objects or an empty stream if the format is not supported
   */
  public abstract <T> Stream<T> input(Class<T> clazz);

  /** The class holding data for this format */
  public abstract Class<?> itemClass();

  /** The name of this format, which must be a valid Shesmu identifier */
  public final String name() {
    return name;
  }

  /** The ASM type for this format. */
  public final Type type() {
    return Type.getType(itemClass());
  }

  /**
   * Write all the data that would be returned by {@link #input(Class)} for {@link #itemClass()} to
   * a JSON output stream.
   */
  public abstract void write(JsonGenerator generator) throws IOException;
}
