package ca.on.oicr.gsi.shesmu.function;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

/**
 * Load any {@link FunctionDefinition} objects available by
 * {@link ServiceLoader}
 *
 * Some functions are directly coded in Java (rather than parsed from files).
 * This interface loads them.
 */
@MetaInfServices
public final class StandardFunctions implements FunctionRepository {

	private static final FunctionDefinition[] FUNCTIONS = new FunctionDefinition[] {
			FunctionDefinition.staticMethod(RuntimeSupport.class, "start_of_day",
					"Rounds a date-time to the previous midnight.", Imyhat.DATE, Imyhat.DATE),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "join_path",
					"Combines two well-formed paths. If the second path is absolute, the first is discarded; if not, they are combined.",
					Imyhat.STRING, Imyhat.STRING, Imyhat.STRING),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "file_name", "Extracts the last element in a path.",
					Imyhat.STRING, Imyhat.STRING),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "dir_name",
					"Extracts all but the last elements in a path (i.e., the containing directory).", Imyhat.STRING,
					Imyhat.STRING) };

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return Stream.of(FUNCTIONS);
	}

}
