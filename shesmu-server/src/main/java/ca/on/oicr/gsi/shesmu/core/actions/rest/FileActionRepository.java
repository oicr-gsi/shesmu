package ca.on.oicr.gsi.shesmu.core.actions.rest;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.FileWatcher;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

@MetaInfServices
public final class FileActionRepository implements ActionRepository {

	private class FileDefinitions extends AutoUpdatingJsonFile<FileDefinition> {
		List<ActionDefinition> definitions = Collections.emptyList();

		public FileDefinitions(Path fileName) {
			super(fileName, FileDefinition.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("File Action Repositories: " + fileName().toString()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Definitions", definitions.size());
				}
			};

		}

		public Stream<ActionDefinition> stream() {
			return definitions.stream();
		}

		@Override
		protected Optional<Integer> update(FileDefinition value) {
			definitions = Stream.of(value.getDefinitions())//
					.map(def -> def.toDefinition(value.getUrl()))//
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
	public Stream<ConfigurationSection> listConfiguration() {
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
