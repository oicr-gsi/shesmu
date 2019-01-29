package ca.on.oicr.gsi.shesmu.plugin.action;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Annotation for defining multiple JSON parameters on an action */
@Retention(RUNTIME)
@Target(TYPE)
public @interface JsonActionParameters {
  JsonActionParameter[] value();
}
