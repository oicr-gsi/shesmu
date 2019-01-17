package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Signer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
