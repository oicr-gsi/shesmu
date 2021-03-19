package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
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
    final var response = RuntimeSupport.MAPPER.createObjectNode();
    final var errorOutput = response.putArray("errors");
    GuidedMeditation.compile(
        Paths.get("Compilation Request.meditation"),
        definitionRepository,
        script,
        errors -> errors.forEach(errorOutput::add),
        result -> response.put("functionBody", result));
    exchange.sendResponseHeaders(200, 0);
    try (final var output = exchange.getResponseBody()) {
      RuntimeSupport.MAPPER.writeValue(output, response);
    }
  }
}
