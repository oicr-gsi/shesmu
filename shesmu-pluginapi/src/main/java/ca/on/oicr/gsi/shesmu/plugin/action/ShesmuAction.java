package ca.on.oicr.gsi.shesmu.plugin.action;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Export an action to Shesmu olives
 *
 * <p>This must be on static method in a subclass of {@link PluginFileType} or a virtual method on a
 * subclass of {@link PluginFile} and it must take no arguments. It must return a subtype of {@link
 * Action}.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuAction {
  public String description() default "";

  public String name() default "";
}
