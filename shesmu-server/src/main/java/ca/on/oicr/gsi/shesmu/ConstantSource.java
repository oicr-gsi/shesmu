package ca.on.oicr.gsi.shesmu;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A service class that can provide external constants that should be visible to
 * Shesmu programs
 */
public interface ConstantSource extends LoadedConfiguration {
	final static ServiceLoader<ConstantSource> LOADER = ServiceLoader.load(ConstantSource.class);

	/**
	 * Get all the constants available
	 */
	public static Stream<Constant> all() {
		return sources().flatMap(ConstantSource::stream);
	}

	/**
	 * Get all the services that can provide constants
	 */
	public static Stream<ConstantSource> sources() {
		return StreamSupport.stream(LOADER.spliterator(), false);
	}

	/**
	 * Provide all constants know by this service
	 */
	Stream<Constant> stream();
}
