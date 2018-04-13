package ca.on.oicr.gsi.shesmu;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A service class that can provide external data that can satisfy the data
 * requirements of {@link Variables}
 *
 */
public interface VariablesSource extends LoadedConfiguration {
	final static ServiceLoader<VariablesSource> LOADER = ServiceLoader.load(VariablesSource.class);

	/**
	 * Get all information currently available
	 */
	public static Stream<Variables> all() {
		return sources().flatMap(VariablesSource::stream);
	}

	/**
	 * Get all the providers of variables
	 *
	 * @return
	 */
	public static Stream<VariablesSource> sources() {
		return StreamSupport.stream(LOADER.spliterator(), false);
	}

	/**
	 * Get all the variables that this instance knows about.
	 *
	 * Duplicates are permitted, but this may be a problem for the receiving Shesmu
	 * script.
	 */
	public Stream<Variables> stream();
}
