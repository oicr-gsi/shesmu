package ca.on.oicr.gsi.shesmu.plugin.refill;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Added to public fields in actions and setter methods in refill that should be available to olives
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface RefillerParameter {
  /** The name for the parameter in Shesmu if different from the Java name */
  public String name() default "";

  /** The Shesmu type descriptor for the setter if not inferred from the Java type */
  public String type() default "";
}
