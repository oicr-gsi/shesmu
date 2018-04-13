package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

/**
 * A source for lookups
 */
public interface LookupRepository extends LoadedConfiguration {

	/**
	 * Query the repository
	 *
	 * @return a stream of pairs containing the name of the lookup and the lookup
	 *         itself
	 */
	Stream<Lookup> query();

}
