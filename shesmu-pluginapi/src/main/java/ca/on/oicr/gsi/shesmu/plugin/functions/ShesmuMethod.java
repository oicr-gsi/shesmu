package ca.on.oicr.gsi.shesmu.plugin.functions;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Make this method available to Shesmu olives as a function or a constant (if it take no
 * arguments).
 *
 * <p>This must be on static method in a subclass of {@link PluginFileType} or a virtual method on a
 * subclass of {@link PluginFile}
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuMethod {
  /**
   * A description of this method as presented in the Shesmu UI.
   *
   * <p>Use <tt>{instance}</tt> and <tt>{file}</tt> to include the name of the instance and file
   * path.
   */
  public String description() default "Too lazy to document.";

  /**
   * The name of the method in Shesmu, if the Java name is not usable.
   *
   * <p>Use <tt>$</tt> to insert an instance name.
   */
  public String name() default "";

  /** The Shesmu type descriptor, if it cannot be inferred from the return type. */
  public String type() default "";
}
