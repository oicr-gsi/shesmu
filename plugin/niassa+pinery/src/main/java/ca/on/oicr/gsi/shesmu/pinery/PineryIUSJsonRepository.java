package ca.on.oicr.gsi.shesmu.pinery;

import java.time.Instant;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.BaseJsonInputRepository;

@MetaInfServices(PineryIUSRepository.class)
public class PineryIUSJsonRepository extends BaseJsonInputRepository<PineryIUSValue> implements PineryIUSRepository {

	public PineryIUSJsonRepository() {
		super("pinery_ius");
	}

	@Override
	protected PineryIUSValue convert(ObjectNode node) {
		final JsonNode ius = node.get("ius");
		final JsonNode lims = node.get("lims");
		return new PineryIUSValue(//
				node.get("path").asText(), //
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
				Instant.ofEpochMilli(node.get("completed_date").asLong()), //
				node.get("is_sample").asBoolean());
	}

}
