package ca.on.oicr.gsi.shesmu.plugin.refill;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Export a refiller to Shesmu olives
 *
 * <p>This must be on static method in a subclass of {@link PluginFileType} or a virtual method on a
 * subclass of {@link PluginFile} and it must take no arguments. It must return a subtype of {@link
 * Refiller} and be parameterised over one type, the input row type.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ShesmuRefill {
  String description() default "";

  String name() default "";
}
