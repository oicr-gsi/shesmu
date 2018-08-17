package ca.on.oicr.gsi.shesmu.signers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Signer;

public class JsonSigner implements Signer<String> {
	private final ObjectNode node = RuntimeSupport.MAPPER.createObjectNode();

	@Override
	public void addVariable(String name, Imyhat type, Object value) {
		type.packJson(node, name, value);
	}

	@Override
	public String finish() {
		try {
			return RuntimeSupport.MAPPER.writeValueAsString(node);
		} catch (final JsonProcessingException e) {
			e.printStackTrace();
			return "";
		}
	}

}
