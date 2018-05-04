package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

/**
 * A source for functions
 */
public interface FunctionRepository extends LoadedConfiguration {

	/**
	 * Query the repository
	 *
	 * @return a stream functions
	 */
	Stream<FunctionDefinition> queryFunctions();

}
