package ca.on.oicr.gsi.shesmu.function;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.FileWatcher;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.WatchedFileListener;
import io.prometheus.client.Gauge;

/**
 * Converts a TSV file into a function
 *
 * The row must be a Shesmu base type for the data in that column. The last
 * column will be treated as the return value and the first columns will be the
 * parameters to match. Every subsequent row is a set of parameters to check,
 * which must either be a value or * to indicate a wild card and a matching
 * return value. If no rows match, the default value for that type is returned.
 */
@MetaInfServices
public class TableFunctionRepository implements FunctionRepository {

	private static final class Table {
		private final List<Object[]> attempts;
		private final Object defaultValue;

		public Table(List<Object[]> attempts, Object defaultValue) {
			super();
			this.attempts = attempts;
			this.defaultValue = defaultValue;
		}

		public Object lookup(Object... parameters) {
			return attempts.stream()//
					.map(attempt -> IntStream.range(0, parameters.length)//
							.allMatch(i -> attempt[i] == null || attempt[i].equals(parameters[i])) //
									? attempt[attempt.length - 1]
									: null)//
					.filter(Objects::nonNull)//
					.findFirst()//
					.orElse(defaultValue);
		}
	}

	private class TableFile implements FunctionDefinition, WatchedFileListener {

		private final Path fileName;

		private final String name;

		private List<FunctionParameter> parameters = Collections.emptyList();

		private Imyhat returnType = Imyhat.BAD;

		public TableFile(Path fileName) {
			this.fileName = fileName;
			name = RuntimeSupport.removeExtension(fileName, EXTENSION);
		}

		public String configuration() {
			return parameters().map(p -> p.type().name())
					.collect(Collectors.joining(", ", "(", ") " + returnType().name()));
		}

		@Override
		public String description() {
			return String.format("Table-defined lookup from %s.", fileName);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Stream<FunctionParameter> parameters() {
			return parameters.stream();
		}

		@Override
		public void render(GeneratorAdapter methodGen) {
			// The arguments are on the stack, unboxed. Create an array to hold them, and
			// work them off the stack into the array, but backwards. Fun.
			final int arrayLocal = methodGen.newLocal(A_OBJECT_ARRAY_TYPE);
			methodGen.push(parameters.size());
			methodGen.newArray(A_OBJECT_TYPE);
			methodGen.storeLocal(arrayLocal);
			for (int i = parameters.size() - 1; i >= 0; i--) {
				final Imyhat type = parameters.get(i).type();
				final int valueLocal = methodGen.newLocal(type.boxedAsmType());
				methodGen.box(type.asmType());
				methodGen.storeLocal(valueLocal);
				methodGen.loadLocal(arrayLocal);
				methodGen.push(i);
				methodGen.loadLocal(valueLocal);
				methodGen.arrayStore(A_OBJECT_TYPE);

			}
			methodGen.push(signature());
			methodGen.loadLocal(arrayLocal);
			methodGen.invokeStatic(A_TABLE_FUNCTION_REPOSITORY_TYPE, METHOD_LOOKUP);
			methodGen.unbox(returnType.asmType());
		}

		@Override
		public Imyhat returnType() {
			return returnType;
		}

		private String signature() {
			return name + "(" + parameters.stream().map(p -> p.type().signature()).collect(Collectors.joining(","))
					+ ")" + returnType.signature();
		}

		@Override
		public void start() {
			update();
		}

		@Override
		public void stop() {

		}

		public Stream<FunctionDefinition> stream() {
			return returnType.isBad() ? Stream.empty() : Stream.of(this);
		}

		@Override
		public Optional<Integer> update() {
			try {
				final List<String> lines = Files.readAllLines(fileName);

				if (lines.size() < 2) {
					tableBad.labels(fileName.toString()).set(1);
					return Optional.empty();
				}

				final List<BaseImyhat> types = TAB.splitAsStream(lines.get(0)).map(Imyhat::forName)
						.collect(Collectors.toList());
				if (types.size() < 2) {
					tableBad.labels(fileName.toString()).set(1);
					return Optional.empty();
				}

				final List<String[]> grid = lines.stream().skip(1).map(TAB::split).collect(Collectors.toList());

				if (grid.stream().anyMatch(columns -> columns.length != types.size())) {
					tableBad.labels(fileName.toString()).set(1);
					return Optional.empty();
				}

				final List<Object[]> attempts = grid.stream()//
						.<Object[]>map(columns -> {
							final Object[] attempt = new Object[types.size()];
							for (int index = 0; index < columns.length; index++) {
								if (index == columns.length - 1 || !columns[index].equals("*")) {
									attempt[index] = types.get(index).parse(columns[index]);
								}
							}
							return attempt;
						}).collect(Collectors.toList());

				returnType = types.get(types.size() - 1);
				parameters = types.stream().limit(types.size() - 1).map(new Function<Imyhat, FunctionParameter>() {
					int index;

					@Override
					public FunctionParameter apply(Imyhat type) {
						return new FunctionParameter(String.format("arg%d", ++index), type);
					}
				}).collect(Collectors.toList());
				TABLES.put(signature(), new Table(attempts, types.get(types.size() - 1).defaultValue()));
				tableBad.labels(fileName.toString()).set(0);
			} catch (final IOException e) {
				e.printStackTrace();
				return Optional.empty();
			}

			return Optional.empty();

		}
	}

	private static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_TABLE_FUNCTION_REPOSITORY_TYPE = Type.getType(TableFunctionRepository.class);

	private static final String EXTENSION = ".lookup";

	private static final Method METHOD_LOOKUP = new Method("lookup", A_OBJECT_TYPE,
			new Type[] { Type.getType(String.class), A_OBJECT_ARRAY_TYPE });

	private static final Pattern TAB = Pattern.compile("\t");

	private static final Gauge tableBad = Gauge.build("shesmu_tsv_lookup_bad", "A TSV lookup table is badly formed.")
			.labelNames("fileName").register();

	/**
	 * We would like the table to be immediately available to olives if changed, but
	 * the number or types of columns could change, in which case, this is very bad,
	 * very quickly. To prevent that, we hold a cache of tables based on the type
	 * signature of the table. The static method is called with the name, which is a
	 * combination of name + type. This means, if the table's type signature
	 * changes, the olive will continue to access the existing orphaned table.
	 */
	private static final Map<String, Table> TABLES = new HashMap<>();

	@RuntimeInterop
	public static Object lookup(String signature, Object... parameters) {
		return TABLES.get(signature).lookup(parameters);
	}

	private final AutoUpdatingDirectory<TableFile> configuration;

	public TableFunctionRepository() {
		this(FileWatcher.DATA_DIRECTORY);
	}

	public TableFunctionRepository(FileWatcher watcher) {
		configuration = new AutoUpdatingDirectory<>(watcher, EXTENSION, TableFile::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		final Map<String, String> map = configuration.stream()
				.collect(Collectors.toMap(t -> t.fileName.toString(), TableFile::configuration));
		return Stream.of(new Pair<>("Table Functions", map));
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return configuration.stream().flatMap(TableFile::stream);
	}

}
