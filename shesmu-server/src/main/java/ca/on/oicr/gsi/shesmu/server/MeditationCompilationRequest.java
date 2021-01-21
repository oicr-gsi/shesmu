package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

public final class MeditationCompilationRequest {
  private String script;

  public String getScript() {
    return script;
  }

  public void setScript(String script) {
    this.script = script;
  }

  public void run(DefinitionRepository definitionRepository, HttpExchange exchange)
      throws IOException, NoSuchAlgorithmException {
    final ObjectNode response = RuntimeSupport.MAPPER.createObjectNode();
    final ArrayNode errorOutput = response.putArray("errors");
    GuidedMeditation.compile(
        Paths.get("Compilation Request.meditation"),
        definitionRepository,
        script,
        errors -> errors.forEach(errorOutput::add),
        result -> response.put("functionBody", result));
    exchange.sendResponseHeaders(200, 0);
    try (final OutputStream output = exchange.getResponseBody()) {
      RuntimeSupport.MAPPER.writeValue(output, response);
    }
  }
}
