package ca.on.oicr.gsi.shesmu.util.definitions;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface ShesmuAction {
	public String description() default "";

	public String name() default "";
}
