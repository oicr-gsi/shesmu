package ca.on.oicr.gsi.shesmu;

import java.io.PrintStream;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A service class that can provide external constants that should be visible to
 * Shesmu programs
 */
public interface DefinitionRepository extends LoadedConfiguration {
	static final ServiceLoader<DefinitionRepository> LOADER = ServiceLoader.load(DefinitionRepository.class);

	/**
	 * Get all the constants available
	 */
	public static Stream<ConstantDefinition> allConstants() {
		return sources().flatMap(DefinitionRepository::constants);
	}

	/**
	 * Get all the functions available
	 */
	public static Stream<FunctionDefinition> allFunctions() {
		return sources().flatMap(DefinitionRepository::functions);
	}

	/**
	 * Get all the actions available
	 */
	public static Stream<ActionDefinition> allActions() {
		return sources().flatMap(DefinitionRepository::actions);
	}

	/**
	 * Get all the services that can provide constants
	 */
	public static Stream<DefinitionRepository> sources() {
		return StreamSupport.stream(LOADER.spliterator(), false);
	}

	/**
	 * Provide all constants know by this service
	 */
	Stream<ConstantDefinition> constants();

	/**
	 * Query the repository
	 *
	 * @return a stream functions
	 */
	Stream<FunctionDefinition> functions();

	/**
	 * Get the known actions
	 *
	 * This can be updated over time.
	 *
	 * @return a stream of definitions for actions the compiler can use
	 */
	Stream<ActionDefinition> actions();

	/**
	 * Write all the JavaScript code needed to pretty print this action.
	 */
	void writeJavaScriptRenderer(PrintStream writer);

}
