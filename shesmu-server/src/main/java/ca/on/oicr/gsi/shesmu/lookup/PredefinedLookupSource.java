package ca.on.oicr.gsi.shesmu.lookup;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.Pair;

/**
 * Load any {@link Lookup} objects available by {@link ServiceLoader}
 * 
 * Some lookups are simple functions that can be coded directly in Java (rather
 * than parsed from files). This interface loads them.
 */
@MetaInfServices
public class PredefinedLookupSource implements LookupRepository {

	private final ServiceLoader<Lookup> loader = ServiceLoader.load(Lookup.class);

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<Lookup> query() {
		return StreamSupport.stream(loader.spliterator(), false);
	}

}
