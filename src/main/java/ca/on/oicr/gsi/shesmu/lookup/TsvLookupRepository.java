package ca.on.oicr.gsi.shesmu.lookup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.Pair;
import io.prometheus.client.Gauge;

@MetaInfServices
public class TsvLookupRepository implements LookupRepository {

	private static final Gauge lastRead = Gauge.build("shesmu_lookup_tsv_config_last_read",
			"The last time, in seconds since the epoch, that the configuration was read.").register();

	private static final Pattern TAB = Pattern.compile("\t");

	public static Optional<String> environmentVariable() {
		return Optional.ofNullable(System.getenv("SHESMU_DATA"));
	}

	private static Stream<Lookup> findLookups(Path path) {
		try (Stream<Path> files = Files.walk(path, 1)) {
			return files//
					.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".lookup"))//
					.map(TsvLookupRepository::readLookup)//
					.filter(Objects::nonNull)//
					.collect(Collectors.toList()).stream();
		} catch (final IOException e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	private static Lookup makeLookup(String name, List<String> lines) {
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

		return new Lookup() {
			@Override
			public Object lookup(Object... parameters) {
				return attempts.stream()//
						.map(attempt -> attempt.apply(parameters))//
						.filter(Optional::isPresent)//
						.findFirst()//
						.orElse(Optional.empty())//
						.orElse(types.get(types.size() - 1).defaultValue());
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public Imyhat returnType() {
				return types.get(types.size() - 1);
			}

			@Override
			public Stream<Imyhat> types() {
				return types.stream().limit(types.size() - 1).map(x -> x);
			}
		};
	}

	public static Stream<Lookup> of(Optional<String> source) {
		return source.map(Paths::get)//
				.filter(Files::exists)//
				.filter(Files::isDirectory)//
				.map(TsvLookupRepository::findLookups)//
				.orElseGet(Stream::empty);
	}

	private static Lookup readLookup(Path lookupFile) {
		try {
			final List<String> lines = Files.readAllLines(lookupFile);
			final String fileName = lookupFile.getFileName().toString();
			return makeLookup(fileName.substring(0, fileName.length() - 7), lines);
		} catch (final IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private final List<Lookup> configuration = new ArrayList<>();

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return environmentVariable().map(path -> {
			Map<String, String> map = new TreeMap<>();
			map.put("path", path);
			return Stream.of(new Pair<>("TSV Lookups", map));

		}).orElse(Stream.empty());
	}

	@Override
	public Stream<Lookup> query() {
		lastRead.setToCurrentTime();
		configuration.clear();
		return of(environmentVariable())//
				.peek(configuration::add);
	}

}
