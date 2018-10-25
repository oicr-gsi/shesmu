package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This field, which must be a public string, must be populated with a comma
 * separated list of SWIDs.
 *
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface AccessionCollection {
	public String name() default "";

}
