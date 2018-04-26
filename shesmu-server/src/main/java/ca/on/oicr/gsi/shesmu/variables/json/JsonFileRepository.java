package ca.on.oicr.gsi.shesmu.variables.json;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.Variables;
import ca.on.oicr.gsi.shesmu.VariablesSource;

@MetaInfServices
public class JsonFileRepository implements VariablesSource {

	private class JsonFile extends AutoUpdatingJsonFile<ObjectNode[]> {

		private final List<Variables> values = Collections.emptyList();

		public JsonFile(Path fileName) {
			super(fileName, ObjectNode[].class);
		}

		@Override
		protected void update(ObjectNode[] values) {
			Stream.of(values).map(node -> new Variables(node.get("accession").asText(), //
					node.get("path").asText(), //
					node.get("metatype").asText(), //
					node.get("md5").asText(), //
					node.get("file_size").asLong(), //
					node.get("workflow").asText(), //
					node.get("workflow_accession").asText(), //
					new Tuple(node.get("workflow_version_0").asLong(), node.get("workflow_version_1").asLong(),
							node.get("workflow_version_2").asLong()), //
					node.get("project").asText(), //
					node.get("library_name").asText(), //
					node.get("donor").asText(), //
					new Tuple(node.get("ius_0").asText(), node.get("ius_1").asLong(), node.get("ius_2").asText()), //
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
					Instant.ofEpochMilli(node.get("timestamp").asLong()), //
					node.get("source").asText())).collect(Collectors.toList());
		}

		public Stream<Variables> variables() {
			return values.stream();
		}
	}

	private final Pair<String, Map<String, String>> configuration;
	private final List<JsonFile> files;

	public JsonFileRepository() {
		files = RuntimeSupport.dataFiles(".variables").map(JsonFile::new).peek(JsonFile::start)
				.collect(Collectors.toList());

		configuration = new Pair<>("Variables from Files",
				files.stream().sorted().collect(Collectors.toMap(new Function<JsonFile, String>() {
					int i;

					@Override
					public String apply(JsonFile t) {
						return Integer.toString(i++);
					}
				}, f -> f.fileName().toString())));
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return files.size() > 0 ? Stream.of(configuration) : Stream.empty();
	}

	@Override
	public Stream<Variables> stream() {
		return files.stream().flatMap(JsonFile::variables);
	}

}
