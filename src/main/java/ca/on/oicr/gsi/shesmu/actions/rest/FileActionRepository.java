package ca.on.oicr.gsi.shesmu.actions.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Pair;

@MetaInfServices
public final class FileActionRepository implements ActionRepository {

	private static final String FILES_VARIABLE = "SHESMU_DATA";

	private static final ObjectMapper mapper = new ObjectMapper();

	public static Optional<String> environmentVariable() {
		return Optional.ofNullable(System.getenv(FILES_VARIABLE));
	}

	public static Stream<ActionDefinition> of(Optional<String> input) {
		return roots(input).flatMap(FileActionRepository::queryActionsCatalog);
	}

	private static Stream<ActionDefinition> queryActionsCatalog(Path file) {
		try {
			final FileDefinition fileDef = mapper.readValue(Files.readAllBytes(file), FileDefinition.class);
			return Arrays.stream(fileDef.getDefinitions()).map(def -> def.toDefinition(fileDef.getUrl()));
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return Stream.empty();
	}

	private static Stream<Path> roots(Optional<String> input) {
		return input.<Stream<Path>>map(directory -> {
			try (Stream<Path> files = Files.walk(Paths.get(directory), 1)) {
				final List<Path> list = files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".actions"))
						.collect(Collectors.toList());
				return list.stream();
			} catch (final IOException e) {
				e.printStackTrace();
				return Stream.empty();
			}
		}).orElse(Stream.empty());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		final Map<String, String> map = new TreeMap<>();
		map.put("path", System.getenv(FILES_VARIABLE));
		return Stream.of(new Pair<>("File Action Repositories", map));
	}

	@Override
	public Stream<ActionDefinition> query() {
		return of(environmentVariable());
	}

}
