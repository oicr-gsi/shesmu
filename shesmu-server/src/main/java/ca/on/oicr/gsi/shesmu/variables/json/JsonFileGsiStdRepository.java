package ca.on.oicr.gsi.shesmu.variables.json;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.GsiStdValue;
import ca.on.oicr.gsi.shesmu.GsiStdRepository;

@MetaInfServices
public class JsonFileGsiStdRepository implements GsiStdRepository {

	private class JsonFile extends AutoUpdatingJsonFile<ObjectNode[]> {

		private final List<GsiStdValue> values = Collections.emptyList();

		public JsonFile(Path fileName) {
			super(fileName, ObjectNode[].class);
		}

		@Override
		protected Optional<Integer> update(ObjectNode[] values) {
			Stream.of(values).map(node -> {
				JsonNode ius = node.get("ius");
				JsonNode workflowVersion = node.get("workflow_version");
				JsonNode lims = node.get("lims");
				return new GsiStdValue(node.get("accession").asText(), //
						node.get("path").asText(), //
						node.get("metatype").asText(), //
						node.get("md5").asText(), //
						node.get("file_size").asLong(), //
						node.get("workflow").asText(), //
						node.get("workflow_accession").asText(), //
						new Tuple(workflowVersion.get(0).asLong(), workflowVersion.get(1).asLong(),
								workflowVersion.get(2).asLong()), //
						node.get("project").asText(), //
						node.get("library_name").asText(), //
						node.get("donor").asText(), //
						new Tuple(ius.get(0).asText(), ius.get(1).asLong(), ius.get(2).asText()), //
						node.get("library_design").asText(), //
						node.get("tissue_type").asText(), //
						node.get("tissue_origin").asText(), //
						node.get("tissue_prep").asText(), //
						node.get("targeted_resequencing").asText(), //
						node.get("tissue_region").asText(), //
						node.get("group_id").asText(), //
						node.get("group_desc").asText(), //
						node.get("library_size").asLong(), //
						node.get("library_type").asText(), //
						node.get("kit").asText(), //
						Instant.ofEpochMilli(node.get("timestamp").asLong()), //
						new Tuple(lims.get(0).asText(), lims.get(1).asText(), lims.get(2).asText()), //
						node.get("source").asText());
			}).collect(Collectors.toList());
			return Optional.empty();
		}

		public Stream<GsiStdValue> variables() {
			return values.stream();
		}
	}

	private final AutoUpdatingDirectory<JsonFile> files;

	public JsonFileGsiStdRepository() {
		files = new AutoUpdatingDirectory<>(".variables", JsonFile::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.of(new Pair<>("Variables from Files",
				files.stream().sorted().collect(Collectors.toMap(new Function<JsonFile, String>() {
					int i;

					@Override
					public String apply(JsonFile t) {
						return Integer.toString(i++);
					}
				}, f -> f.fileName().toString()))));
	}

	@Override
	public Stream<GsiStdValue> stream() {
		return files.stream().flatMap(JsonFile::variables);
	}

}
