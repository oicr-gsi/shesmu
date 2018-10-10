package ca.on.oicr.gsi.shesmu.core;

import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.status.ConfigurationSection;

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
					"Rounds a date-time to the previous midnight.", Imyhat.DATE,
					new FunctionParameter("raw_date", Imyhat.DATE)),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "join_path",
					"Combines two well-formed paths. If the second path is absolute, the first is discarded; if not, they are combined.",
					Imyhat.STRING, new FunctionParameter("working_directory", Imyhat.STRING),
					new FunctionParameter("child path", Imyhat.STRING)),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "file_name", "Extracts the last element in a path.",
					Imyhat.STRING, new FunctionParameter("input path", Imyhat.STRING)),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "dir_name",
					"Extracts all but the last elements in a path (i.e., the containing directory).", Imyhat.STRING,
					new FunctionParameter("input path", Imyhat.STRING)),
			FunctionDefinition.staticMethod(RuntimeSupport.class, "version_at_least",
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
				public Imyhat returnType() {
					return Imyhat.INTEGER;
				}

				@Override
				public void render(GeneratorAdapter methodGen) {
					methodGen.invokeVirtual(Type.getType(String.class),
							new Method("length", Type.INT_TYPE, new Type[] {}));
					methodGen.cast(Type.INT_TYPE, Type.LONG_TYPE);
				}

				@Override
				public Stream<FunctionParameter> parameters() {
					return Stream.of(new FunctionParameter("str", Imyhat.STRING));
				}

				@Override
				public String name() {
					return "str_len";
				}

				@Override
				public String description() {
					return "Gets the length of a string.";
				}

				@Override
				public void renderStart(GeneratorAdapter methodGen) {
					// None required.
				}
			} };

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return Stream.of(FUNCTIONS);
	}

}
