package ca.on.oicr.gsi.shesmu.core.actions.rest;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
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
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.FileWatcher;

@MetaInfServices
public final class FileActionRepository implements ActionRepository {

	private class FileDefinitions extends AutoUpdatingJsonFile<FileDefinition> {
		List<ActionDefinition> definitions = Collections.emptyList();

		private final Map<String, String> map = new TreeMap<>();

		public FileDefinitions(Path fileName) {
			super(fileName, FileDefinition.class);
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>("File Action Repositories: " + fileName().toString(), map);

		}

		public Stream<ActionDefinition> stream() {
			return definitions.stream();
		}

		@Override
		protected Optional<Integer> update(FileDefinition value) {
			map.put("path", value.getUrl());
			definitions = Stream.of(value.getDefinitions()).map(def -> def.toDefinition(value.getUrl()))
					.collect(Collectors.toList());
			return Optional.empty();
		}
	}

	private final AutoUpdatingDirectory<FileDefinitions> roots;

	public FileActionRepository() {
		this(FileWatcher.DATA_DIRECTORY);
	}

	public FileActionRepository(FileWatcher watcher) {
		roots = new AutoUpdatingDirectory<>(watcher, ".actions", FileDefinitions::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return roots.stream().map(FileDefinitions::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return roots.stream().flatMap(FileDefinitions::stream);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		// Do nothing. The actions are the same as RemoteActionRepository
	}

}
