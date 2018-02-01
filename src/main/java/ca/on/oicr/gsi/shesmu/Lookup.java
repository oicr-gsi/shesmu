package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

/**
 * A multi-keyed map that lookups a value based on rules/tables
 */
public interface Lookup {

	/**
	 * Fetch a value for the specified parameters
	 *
	 * @param parameters
	 *            the parameters, to be cast to the correct types
	 * @return the value or null if no match is available
	 */
	@RuntimeInterop
	Object lookup(Object... parameters);

	/**
	 * The name of the lookup.
	 */
	String name();

	/**
	 * The return type of the map
	 */
	Imyhat returnType();

	/**
	 * The types of the parameters
	 */
	Stream<Imyhat> types();
}
