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

	public static Stream<Constant> all() {
		return sources().flatMap(ConstantSource::stream);
	}

	public static Stream<ConstantSource> sources() {
		return StreamSupport.stream(LOADER.spliterator(), false);
	}

	Stream<Constant> stream();
}
