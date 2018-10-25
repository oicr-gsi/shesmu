package ca.on.oicr.gsi.shesmu.core;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.status.ConfigurationSection;

/**
 * The standard library of Shesmu
 */
@MetaInfServices(DefinitionRepository.class)
public final class StandardDefinitions implements DefinitionRepository {

	/**
	 * Get the directory name of a path
	 */
	public static String dir_name(String input) {
		final Path path = Paths.get(input).getParent();
		return path == null ? "" : path.toString();
	}

	/**
	 * Get the file name (basename) of a path
	 */
	public static String file_name(String input) {
		return Paths.get(input).getFileName().toString();

	}

	/**
	 * Join two paths
	 */
	public static String join_path(String dir, String file) {
		return Paths.get(dir).resolve(file).toString();
	}

	/**
	 * Truncate a time stamp to midnight
	 */
	public static Instant start_of_day(Instant input) {
		return input.truncatedTo(ChronoUnit.DAYS);
	}

	public static boolean version_at_least(Tuple version, long major, long minor, long patch) {
		if ((Long) version.get(0) < major) {
			return false;
		}
		if ((Long) version.get(1) < minor) {
			return false;
		}
		return (Long) version.get(2) >= patch;

	}

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);

	private static final Type A_NOTHING_ACTION_TYPE = Type.getType(NothingAction.class);

	private static final ConstantDefinition[] CONSTANTS = new ConstantDefinition[] {
			ConstantDefinition.of("epoch", Instant.EPOCH, "The date at UNIX timestamp 0: 1970-01-01T00:00:00Z"),
			new ConstantDefinition("now", Imyhat.DATE,
					"The current timestamp. This is fetched every time this constant is referenced, so now != now.") {

				@Override
				protected void load(GeneratorAdapter methodGen) {
					methodGen.invokeStatic(A_INSTANT_TYPE, METHOD_INSTANT__NOW);
				}
			} };
	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
	private static final FunctionDefinition[] FUNCTIONS = new FunctionDefinition[] {
			FunctionDefinition.staticMethod("start_of_day", StandardDefinitions.class, "start_of_day",
					"Rounds a date-time to the previous midnight.", Imyhat.DATE,
					new FunctionParameter("date", Imyhat.DATE)),
			FunctionDefinition.staticMethod("join_path", StandardDefinitions.class, "join_path",
					"Combines two well-formed paths. If the second path is absolute, the first is discarded; if not, they are combined.",
					Imyhat.STRING, new FunctionParameter("working directory", Imyhat.STRING),
					new FunctionParameter("child path", Imyhat.STRING)),
			FunctionDefinition.staticMethod("file_name", StandardDefinitions.class, "file_name",
					"Extracts the last element in a path.", Imyhat.STRING,
					new FunctionParameter("input path", Imyhat.STRING)),
			FunctionDefinition.staticMethod("dir_name", StandardDefinitions.class, "dir_name",
					"Extracts all but the last elements in a path (i.e., the containing directory).", Imyhat.STRING,
					new FunctionParameter("input path", Imyhat.STRING)),
			FunctionDefinition.staticMethod("version_at_least", StandardDefinitions.class, "version_at_least",
					"Checks whether the supplied version tuple is the same or greater than version numbers provided.",
					Imyhat.BOOLEAN,
					new FunctionParameter("version", Imyhat.tuple(Imyhat.INTEGER, Imyhat.INTEGER, Imyhat.INTEGER)),
					new FunctionParameter("major", Imyhat.INTEGER), new FunctionParameter("minor", Imyhat.INTEGER),
					new FunctionParameter("patch", Imyhat.INTEGER)),
			FunctionDefinition.virtualMethod("str_trim", "trim", "Remove white space from a string.", Imyhat.STRING,
					new FunctionParameter("str", Imyhat.STRING)),
			FunctionDefinition.virtualMethod("str_lower", "toLowerCase", "Convert a string to lower case.",
					Imyhat.STRING, new FunctionParameter("str", Imyhat.STRING)),
			FunctionDefinition.virtualMethod("str_upper", "toUpperCase", "Convert a string to upper case.",
					Imyhat.STRING, new FunctionParameter("str", Imyhat.STRING)),
			FunctionDefinition.virtualMethod("str_eq", "equalsIgnoreCase", "Compares two strings ignoring case.",
					Imyhat.BOOLEAN, new FunctionParameter("first", Imyhat.STRING),
					new FunctionParameter("second", Imyhat.STRING)),
			new FunctionDefinition() {

				@Override
				public String description() {
					return "Gets the length of a string.";
				}

				@Override
				public String name() {
					return "str_len";
				}

				@Override
				public Stream<FunctionParameter> parameters() {
					return Stream.of(new FunctionParameter("str", Imyhat.STRING));
				}

				@Override
				public void render(GeneratorAdapter methodGen) {
					methodGen.invokeVirtual(Type.getType(String.class),
							new Method("length", Type.INT_TYPE, new Type[] {}));
					methodGen.cast(Type.INT_TYPE, Type.LONG_TYPE);
				}

				@Override
				public void renderStart(GeneratorAdapter methodGen) {
					// None required.
				}

				@Override
				public Imyhat returnType() {
					return Imyhat.INTEGER;
				}
			} };
	private static final Method METHOD_INSTANT__NOW = new Method("now", A_INSTANT_TYPE, new Type[] {});

	private static final ActionDefinition NOTHING_ACTION = new ActionDefinition("nothing", A_NOTHING_ACTION_TYPE,
			"Does absolutely nothing and ignores the value provided. Useful for debugging.",
			Stream.of(ActionParameterDefinition.forField("value", "value", Imyhat.STRING, true))) {

		@Override
		public void initialize(GeneratorAdapter methodGen) {
			methodGen.newInstance(A_NOTHING_ACTION_TYPE);
			methodGen.dup();
			methodGen.invokeConstructor(A_NOTHING_ACTION_TYPE, DEFAULT_CTOR);
		}

	};

	@Override
	public Stream<ActionDefinition> actions() {
		return Stream.of(NOTHING_ACTION);
	}

	@Override
	public Stream<ConstantDefinition> constants() {
		return Stream.of(CONSTANTS);
	}

	@Override
	public Stream<FunctionDefinition> functions() {
		return Stream.of(FUNCTIONS);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print("actionRender.set('nothing', a => [title(a, 'Nothing'), text(`Value: ${a.value}`)]);");
	}

}
