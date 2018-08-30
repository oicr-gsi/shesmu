package ca.on.oicr.gsi.shesmu.input.nabu;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TreeSet;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.input.BaseJsonInputRepository;

@MetaInfServices(NabuRepository.class)
public class NabuJsonRepository extends BaseJsonInputRepository<NabuValue> implements NabuRepository {

	public NabuJsonRepository() {
		super("nabu");
	}

	@Override
	protected NabuValue convert(ObjectNode node) {
		final Set<Long> upstream = new TreeSet<>();
		for (JsonNode value : node.get("upstream")) {
			upstream.add(value.asLong());
		}
		final JsonNode qcdate = node.get("qcdate");
		Instant date;
		if (qcdate == null || qcdate.isNull()) {
			date = Instant.EPOCH;
		} else if (qcdate.isLong()) {
			date = Instant.ofEpochMilli(qcdate.asLong());
		} else {
			date = DateTimeFormatter.ISO_INSTANT.parse(qcdate.asText(), Instant::from);
		}

		return new NabuValue(//
				node.get("fileswid").asInt(), //
				node.get("filepath").asText(), //
				node.get("qcstatus").asText(), //
				node.get("username").asText(), //
				node.get("comment").asText(), //
				node.get("project").asText(), //
				node.get("stalestatus").asText(), //
				date, //
				upstream, //
				node.get("skip").asBoolean(false));
	}

}
