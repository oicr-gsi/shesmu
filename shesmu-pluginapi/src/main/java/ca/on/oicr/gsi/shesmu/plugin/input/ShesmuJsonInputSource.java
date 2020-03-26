package ca.on.oicr.gsi.shesmu.plugin.input;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicate this method returns input for a particular format encoded as a bytestream of JSON data
 *
 * <p>The input format must be separately defined by {@link InputFormat}
 *
 * <p>This annotation should be applied to instance methods in {@link PluginFile} or static methods
 * {@link PluginFileType} that return an {@link InputStream} and take no arguments. The stream will
 * be closed after reading is complete.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuJsonInputSource {
  /**
   * The name of the input format.
   *
   * <p>If the format is unrecognised, this method will be ignored.
   */
  String format();

  /** The length of time in minutes to cache the output of this method before reading again. */
  int ttl() default 60;
}
