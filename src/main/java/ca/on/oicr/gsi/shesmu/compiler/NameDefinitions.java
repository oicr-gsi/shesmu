package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

/**
 * A collection of all defined variables at any point in a program.
 *
 * Also tracks if the program has resolved all variables so far.
 */
public class NameDefinitions {
	private static class DefaultStreamTarget extends Target {
		private final String name;
		private final Imyhat type;

		public DefaultStreamTarget(Entry<String, Imyhat> entry) {
			name = entry.getKey();
			type = entry.getValue();
		}

		@Override
		public Flavour flavour() {
			return Flavour.STREAM;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Imyhat type() {
			return type;
		}

	}

	private static final Map<String, Imyhat> BASE_VARIABLES = new HashMap<>();

	static {
		BASE_VARIABLES.put("accession", Imyhat.INTEGER);
		BASE_VARIABLES.put("donor", Imyhat.STRING.asList());
		BASE_VARIABLES.put("file_size", Imyhat.INTEGER);
		BASE_VARIABLES.put("group_desc", Imyhat.STRING.asList());
		BASE_VARIABLES.put("group_id", Imyhat.STRING.asList());
		BASE_VARIABLES.put("ius", Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING));
		BASE_VARIABLES.put("library_sample", Imyhat.STRING.asList());
		BASE_VARIABLES.put("library_size", Imyhat.INTEGER.asList());
		BASE_VARIABLES.put("library_template_type", Imyhat.STRING.asList());
		BASE_VARIABLES.put("library_type", Imyhat.STRING.asList());
		BASE_VARIABLES.put("md5", Imyhat.STRING);
		BASE_VARIABLES.put("metatype", Imyhat.STRING);
		BASE_VARIABLES.put("path", Imyhat.STRING);
		BASE_VARIABLES.put("study", Imyhat.STRING.asList());
		BASE_VARIABLES.put("targeted_resequencing", Imyhat.STRING.asList());
		BASE_VARIABLES.put("tissue_origin", Imyhat.STRING.asList());
		BASE_VARIABLES.put("tissue_prep", Imyhat.STRING.asList());
		BASE_VARIABLES.put("tissue_region", Imyhat.STRING.asList());
		BASE_VARIABLES.put("tissue_type", Imyhat.STRING.asList());
		BASE_VARIABLES.put("workflow", Imyhat.STRING);
		BASE_VARIABLES.put("workflow_version", Imyhat.tuple(Imyhat.INTEGER, Imyhat.INTEGER, Imyhat.INTEGER));
	}

	public static Stream<Target> baseStreamVariables() {
		return BASE_VARIABLES.entrySet().stream().map(DefaultStreamTarget::new);
	}

	/**
	 * Create a new collection of variables from the parameters provided.
	 *
	 * @param parameters
	 *            the parameters for this environment
	 */
	public static NameDefinitions root(Stream<? extends Target> parameters) {
		return new NameDefinitions(Stream
				.concat(parameters.filter(variable -> variable.flavour() == Flavour.PARAMETER), baseStreamVariables())
				.collect(Collectors.toMap(Target::name, Function.identity())), true);
	}

	private final boolean isGood;

	private final Map<String, Target> variables;

	private NameDefinitions(Map<String, Target> variables, boolean isGood) {
		this.variables = variables;
		this.isGood = isGood;
	}

	/**
	 * Bind a lambda parameter to the known names
	 *
	 * This will eclipse any existing definition
	 *
	 * @param parameter
	 *            the parameter to bind
	 * @return
	 */
	public NameDefinitions bind(Target parameter) {
		return new NameDefinitions(
				Stream.concat(variables.values().stream().filter(variable -> !variable.name().equals(parameter.name())),
						Stream.of(parameter)).collect(Collectors.toMap(Target::name, Function.identity())),
				isGood);
	}

	/**
	 * Create a new set of defined variables that is identical, but mark it as a
	 * failure.
	 */
	public NameDefinitions fail(boolean ok) {
		return new NameDefinitions(variables, ok && isGood);
	}

	/**
	 * Get a variable from the collection.
	 */
	public Optional<Target> get(String name) {
		return Optional.ofNullable(variables.get(name));
	}

	/**
	 * Determine if any failures have occurred so far.
	 */
	public boolean isGood() {
		return isGood;
	}

	/**
	 * Create a new set of defined names, replacing the stream variables with the
	 * ones provided
	 *
	 * @param newStreamVariables
	 *            the new stream variables to be inserted
	 * @param good
	 *            whether a failure has occurred
	 */
	public NameDefinitions replaceStream(Stream<Target> newStreamVariables, boolean good) {
		return new NameDefinitions(
				Stream.concat(variables.values().stream().filter(variable -> variable.flavour() != Flavour.STREAM),
						newStreamVariables).collect(Collectors.toMap(Target::name, Function.identity())),
				isGood && good);
	}

	public Stream<Target> stream() {
		return variables.values().stream();
	}
}