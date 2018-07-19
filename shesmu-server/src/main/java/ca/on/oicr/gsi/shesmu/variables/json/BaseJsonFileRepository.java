package ca.on.oicr.gsi.shesmu.variables.json;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.InputRepository;
import ca.on.oicr.gsi.shesmu.Pair;

public abstract class BaseJsonFileRepository<V> implements InputRepository<V> {

	private class JsonFile extends AutoUpdatingJsonFile<ObjectNode[]> {

		private List<V> values = Collections.emptyList();

		public JsonFile(Path fileName) {
			super(fileName, ObjectNode[].class);
		}

		@Override
		protected Optional<Integer> update(ObjectNode[] values) {
			this.values = Stream.of(values).map(BaseJsonFileRepository.this::convert).collect(Collectors.toList());
			return Optional.empty();
		}

		public Stream<V> variables() {
			return values.stream();
		}
	}

	private final AutoUpdatingDirectory<JsonFile> files;
	private final String inputFormatName;

	public BaseJsonFileRepository(String inputFormatName) {
		this.inputFormatName = inputFormatName;
		files = new AutoUpdatingDirectory<>("." + inputFormatName, JsonFile::new);
	}

	protected abstract V convert(ObjectNode node);

	@Override
	public final Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.of(new Pair<>(String.format("Variables from Files (%s Format)", inputFormatName),
				files.stream().sorted().collect(Collectors.toMap(new Function<JsonFile, String>() {
					int i;

					@Override
					public String apply(JsonFile t) {
						return Integer.toString(i++);
					}
				}, f -> f.fileName().toString()))));
	}

	@Override
	public final Stream<V> stream() {
		return files.stream().flatMap(JsonFile::variables);
	}

}
