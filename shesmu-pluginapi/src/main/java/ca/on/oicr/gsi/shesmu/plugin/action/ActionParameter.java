package ca.on.oicr.gsi.shesmu.plugin.action;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Added to public fields in actions and setter methods in actions that should be available to
 * olives
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface ActionParameter {
  /**
   * The name for the parameter in Shesmu if different from the Java name
   *
   * @return the parameter name or empty string
   */
  String name() default "";

  /**
   * Whether the parameter is required
   *
   * @return true if the parameter must be supplied by the olive
   */
  boolean required() default true;

  /**
   * The Shesmu type descriptor for the setter if not inferred from the Java type
   *
   * @return the type descriptor or empty string
   */
  String type() default "";
}
