package ca.on.oicr.gsi.shesmu.variables.json;

import java.time.Instant;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.GsiStdRepository;
import ca.on.oicr.gsi.shesmu.GsiStdValue;
import ca.on.oicr.gsi.shesmu.Tuple;

@MetaInfServices(GsiStdRepository.class)
public class JsonFileGsiStdRepository extends BaseJsonFileRepository<GsiStdValue> implements GsiStdRepository {

	public JsonFileGsiStdRepository() {
		super("gsi_std");
	}

	@Override
	protected GsiStdValue convert(ObjectNode node) {
		final JsonNode ius = node.get("ius");
		final JsonNode workflowVersion = node.get("workflow_version");
		final JsonNode lims = node.get("lims");
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
	}

}
