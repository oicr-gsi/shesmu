package ca.on.oicr.gsi.shesmu.plugin.functions;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Extra information on a parameter when exporting a Java method into Shesmu */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface ShesmuParameter {
  /** The description of the parameter */
  public String description();

  /** The Shesmu type descriptor if it cannot be inferred automatically. */
  public String type() default "";
}
