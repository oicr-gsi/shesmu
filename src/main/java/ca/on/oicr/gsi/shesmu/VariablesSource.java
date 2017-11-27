package ca.on.oicr.gsi.shesmu;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A service class that can provide external data that can satisfy the data
 * requirements of {@link Variables}
 *
 */
public interface VariablesSource {
	final static ServiceLoader<VariablesSource> LOADER = ServiceLoader.load(VariablesSource.class);

	public static Stream<Variables> all() {
		return StreamSupport.stream(LOADER.spliterator(), false).flatMap(VariablesSource::stream);
	}

	public Stream<Variables> stream();
}
