package ca.on.oicr.gsi.shesmu;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a method in {@link Variables} is exported to the
 * Shesmu compiler
 *
 * The name of the method must be a valid Shesmu identifier
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Export {
	/**
	 * A string containing the {@link Imyhat} type signature of the return type of
	 * the method
	 */
	public String type();
}
