package ca.on.oicr.gsi.shesmu.plugin.signature;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicate this method returns a signer
 *
 * <p>This annotation should be applied to static instance methods in {@link PluginFile} or static
 * methods in {@link PluginFileType} that return new instances of {@link DynamicSigner} or {@link
 * StaticSigner} and take no arguments constructor.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuSigner {
  /**
   * The name of the signer variable
   *
   * <p>If empty, the method name will be used. If applied to a {@link PluginFile} method, it must
   * have a <code>$</code> which will contain the instance name
   */
  String name() default "";

  /** The Shesmu type descriptor that the signature returns */
  String type();
}
