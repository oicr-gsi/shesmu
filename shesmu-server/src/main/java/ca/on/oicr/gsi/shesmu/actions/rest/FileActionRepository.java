package ca.on.oicr.gsi.shesmu.actions.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

@MetaInfServices
public final class FileActionRepository implements ActionRepository {

	private static Stream<FileDefinition> files(Optional<String> input) {
		return RuntimeSupport.dataFiles(RuntimeSupport.dataDirectory(input), FileDefinition.class, ".actions");
	}

	public static Stream<ActionDefinition> of(Optional<String> input) {
		return of(files(input));
	}

	public static Stream<ActionDefinition> of(Stream<FileDefinition> input) {
		return input.flatMap(
				fileDef -> Arrays.stream(fileDef.getDefinitions()).map(def -> def.toDefinition(fileDef.getUrl())));
	}

	private final List<FileDefinition> roots = files(RuntimeSupport.environmentVariable()).collect(Collectors.toList());

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return roots.stream().map(fileDefinition -> {
			final Map<String, String> map = new TreeMap<>();
			map.put("path", fileDefinition.getUrl());
			return new Pair<>("File Action Repositories", map);
		});
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return of(roots.stream());
	}

}
