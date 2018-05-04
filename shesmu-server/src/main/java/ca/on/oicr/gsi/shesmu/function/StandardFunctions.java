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
			FunctionDefinition.staticMethod(RuntimeSupport.class, "start_of_day", Imyhat.DATE, Imyhat.DATE),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "join_path", Imyhat.STRING, Imyhat.STRING,
					Imyhat.STRING),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "file_name", Imyhat.STRING, Imyhat.STRING),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "dir_name", Imyhat.STRING, Imyhat.STRING) };

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return Stream.of(FUNCTIONS);
	}

}
