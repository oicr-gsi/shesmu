package ca.on.oicr.gsi.shesmu.util.input;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

/**
 * Annotation to indicate that a method in a value class is exported to the
 * Shesmu compiler via {@link InputFormatDefinition}
 *
 * The name of the method must be a valid Shesmu identifier
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Export {
	/**
	 * Whether the value should be included in the automatic signature generation
	 */
	public boolean signable() default false;

	/**
	 * A string containing the {@link Imyhat} type signature of the return type of
	 * the method
	 */
	public String type();
}
