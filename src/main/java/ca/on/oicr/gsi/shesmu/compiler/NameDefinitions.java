package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Arrays;
import java.util.Map;
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

		public DefaultStreamTarget(String name, Imyhat type) {
			this.name = name;
			this.type = type;
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

	private static final Target[] BASE_VARIABLES = new Target[] { //
			new DefaultStreamTarget("accession", Imyhat.STRING), //
			new DefaultStreamTarget("donor", Imyhat.STRING), //
			new DefaultStreamTarget("file_size", Imyhat.INTEGER), //
			new DefaultStreamTarget("group_desc", Imyhat.STRING), //
			new DefaultStreamTarget("group_id", Imyhat.STRING), //
			new DefaultStreamTarget("ius", Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING)), //
			new DefaultStreamTarget("library_sample", Imyhat.STRING), //
			new DefaultStreamTarget("library_size", Imyhat.INTEGER), //
			new DefaultStreamTarget("library_template_type", Imyhat.STRING), //
			new DefaultStreamTarget("library_type", Imyhat.STRING), //
			new DefaultStreamTarget("md5", Imyhat.STRING), //
			new DefaultStreamTarget("metatype", Imyhat.STRING), //
			new DefaultStreamTarget("path", Imyhat.STRING), //
			new DefaultStreamTarget("source", Imyhat.STRING), //
			new DefaultStreamTarget("study", Imyhat.STRING), //
			new DefaultStreamTarget("targeted_resequencing", Imyhat.STRING), //
			new DefaultStreamTarget("tissue_origin", Imyhat.STRING), //
			new DefaultStreamTarget("tissue_prep", Imyhat.STRING), //
			new DefaultStreamTarget("tissue_region", Imyhat.STRING), //
			new DefaultStreamTarget("tissue_type", Imyhat.STRING), //
			new DefaultStreamTarget("workflow", Imyhat.STRING), //
			new DefaultStreamTarget("workflow_version", Imyhat.tuple(Imyhat.INTEGER, Imyhat.INTEGER, Imyhat.INTEGER)) };

	public static Stream<Target> baseStreamVariables() {
		return Arrays.stream(BASE_VARIABLES);
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
