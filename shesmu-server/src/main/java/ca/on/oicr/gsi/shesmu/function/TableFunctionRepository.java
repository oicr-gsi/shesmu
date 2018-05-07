package ca.on.oicr.gsi.shesmu.function;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
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

	private static final Gauge lastRead = Gauge.build("shesmu_table_function_config_last_read",
			"The last time, in seconds since the epoch, that the configuration was read.").register();

	private static final Pattern TAB = Pattern.compile("\t");

	@RuntimeInterop
	private static Object lookup(List<Function<Object[], Optional<Object>>> attempts, Object defaultValue,
			Object... parameters) {
		return attempts.stream()//
				.map(attempt -> attempt.apply(parameters))//
				.filter(Optional::isPresent)//
				.findFirst()//
				.orElse(Optional.empty())//
				.orElse(defaultValue);
	}

	private static FunctionDefinition makeLookup(String name, List<String> lines) {
		if (lines.size() < 2) {
			return null;
		}

		final List<BaseImyhat> types = TAB.splitAsStream(lines.get(0)).map(Imyhat::forName)
				.collect(Collectors.toList());
		if (types.size() < 2) {
			return null;
		}

		final List<String[]> grid = lines.stream().skip(1).map(TAB::split).collect(Collectors.toList());

		if (grid.stream().anyMatch(columns -> columns.length != types.size())) {
			return null;
		}

		final List<Function<Object[], Optional<Object>>> attempts = grid.stream()//
				.<Function<Object[], Optional<Object>>>map(columns -> {
					Predicate<Object[]> combiningPredicates = x -> true;
					for (int index = 0; index < columns.length - 1; index++) {
						if (!columns[index].equals("*")) {
							final Object match = types.get(index).parse(columns[index]);
							final int i = index;
							combiningPredicates = combiningPredicates.and(parameters -> parameters[i].equals(match));
						}
					}

					final Predicate<Object[]> predicate = combiningPredicates;
					final Object result = types.get(types.size() - 1).parse(columns[columns.length - 1]);
					return parameters -> predicate.test(parameters) ? Optional.of(result) : Optional.empty();
				}).collect(Collectors.toList());

		return FunctionForInstance.bind(TableFunctionRepository.class, mt -> {
			try {
				final MethodHandle lookupMethod = MethodHandles.lookup().findStatic(TableFunctionRepository.class,
						"lookup", MethodType.methodType(Object.class, List.class, Object.class, Object[].class));
				return lookupMethod.bindTo(attempts).bindTo(types.get(types.size() - 1).defaultValue())
						.asVarargsCollector(Object[].class).asType(mt);
			} catch (NoSuchMethodException | IllegalAccessException e) {
				return MethodHandles.throwException(types.get(types.size() - 1).javaType(),
						UnsupportedOperationException.class);
			}
		}, name, types.get(types.size() - 1),
				types.stream().limit(types.size() - 1).map(x -> x).toArray(Imyhat[]::new));
	}

	public static Stream<FunctionDefinition> of(Optional<String> source) {
		return RuntimeSupport.dataFilesForPath(source, ".lookup").map(TableFunctionRepository::readLookup);
	}

	private static FunctionDefinition readLookup(Path lookupFile) {
		try {
			final List<String> lines = Files.readAllLines(lookupFile);
			final String fileName = lookupFile.getFileName().toString();
			return makeLookup(fileName.substring(0, fileName.length() - 7), lines);
		} catch (final IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private final List<FunctionDefinition> configuration = new ArrayList<>();

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return RuntimeSupport.environmentVariable().map(path -> {
			final Map<String, String> map = new TreeMap<>();
			map.put("path", path);
			return Stream.of(new Pair<>("Table Functions", map));
		}).orElse(Stream.empty());
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		lastRead.setToCurrentTime();
		configuration.clear();
		return of(RuntimeSupport.environmentVariable())//
				.peek(configuration::add);
	}

}
