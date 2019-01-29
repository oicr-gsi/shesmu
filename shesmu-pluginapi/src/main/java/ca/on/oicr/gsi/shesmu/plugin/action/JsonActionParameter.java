package ca.on.oicr.gsi.shesmu.plugin.action;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Add an argument to an action that will be converted to JSON
 *
 * <p>This should only be applied to actions that are derived from {@link JsonParameterisedAction}
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(JsonActionParameters.class)
public @interface JsonActionParameter {
  /** The name of the parameter in Shesmu and in the JSON output */
  public String name();

  /** Whether the parameter is required or optional */
  public boolean required() default true;

  /** The Shesmu type descriptor for this parameter */
  public String type();
}
