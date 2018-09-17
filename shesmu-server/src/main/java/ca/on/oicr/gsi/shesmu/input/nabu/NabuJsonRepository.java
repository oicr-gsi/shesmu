package ca.on.oicr.gsi.shesmu.input.nabu;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

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
		final JsonNode qcstatus = node.get("qcstatus");
		String status;
		if (qcstatus == null || qcstatus.isNull()) {
			status = "PENDING";
		} else if (qcstatus.isBoolean()) {
			status = qcstatus.asBoolean() ? "PASSED" : "FAILED";
		} else {
			status = qcstatus.asText();
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
				status, //
				node.get("username").asText(), //
				node.get("comment").asText(""), //
				node.get("project").asText(), //
				date);
	}

}
