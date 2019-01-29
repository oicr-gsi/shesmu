package ca.on.oicr.gsi.shesmu.plugin.input;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.stream.Stream;

/**
 * Indicate this method returns input for a particular format
 *
 * <p>The input format must be separately defined by {@link InputFormat}
 *
 * <p>This annotation should be applied to instance methods in {@link PluginFile} or static methods
 * {@link PluginFileType} that return a {@link Stream} of input value and take no arguments. The
 * generic type of the string must match with an existing input format.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuInputSource {}
