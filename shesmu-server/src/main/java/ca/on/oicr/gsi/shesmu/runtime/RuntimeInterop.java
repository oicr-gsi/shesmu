package ca.on.oicr.gsi.shesmu.runtime;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This method is called by code generated by the Shesmu compiler.
 *
 * <p>It should not be considered unused and any changes to it must be matched by changes in the
 * code generator.
 */
@Retention(SOURCE)
@Target({CONSTRUCTOR, METHOD, FIELD})
public @interface RuntimeInterop {}
