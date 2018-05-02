package ca.on.oicr.gsi.shesmu.lookup;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

/**
 * Load any {@link LookupDefinition} objects available by {@link ServiceLoader}
 *
 * Some lookups are simple functions that can be coded directly in Java (rather
 * than parsed from files). This interface loads them.
 */
@MetaInfServices
public class StandardLookups implements LookupRepository {

	private static final LookupDefinition[] LOOKUPS = new LookupDefinition[] {
			LookupDefinition.staticMethod(RuntimeSupport.class, "start_of_day", Imyhat.DATE, Imyhat.DATE),
			LookupDefinition.staticMethod(RuntimeSupport.class, "join_path", Imyhat.STRING, Imyhat.STRING, Imyhat.STRING),
			LookupDefinition.staticMethod(RuntimeSupport.class, "file_name", Imyhat.STRING, Imyhat.STRING),
			LookupDefinition.staticMethod(RuntimeSupport.class, "dir_name", Imyhat.STRING, Imyhat.STRING) };

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<LookupDefinition> queryLookups() {
		return Stream.of(LOOKUPS);
	}

}
