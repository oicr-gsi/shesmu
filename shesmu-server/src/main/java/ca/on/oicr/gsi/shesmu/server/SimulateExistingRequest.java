package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class SimulateExistingRequest extends BaseSimulateRequest {
  private String fileName;

  @Override
  protected boolean allowUnused() {
    return false;
  }

  @Override
  protected boolean dryRun() {
    return false;
  }

  @Override
  protected Stream<FakeActionDefinition> fakeActions() {
    return Stream.empty();
  }

  @Override
  protected Stream<FakeRefillerDefinition> fakeRefillers() {
    return Stream.empty();
  }

  public String getFileName() {
    return fileName;
  }

  @Override
  protected boolean keepNativeAction(String name) {
    return true;
  }

  public void run(
      CompiledGenerator compiler,
      DefinitionRepository definitionRepository,
      ActionServices actionServices,
      InputSource inputSource,
      HttpExchange http)
      throws IOException {
    if (compiler.dashboard().noneMatch(p -> p.second().filename().equals(fileName))) {
      http.sendResponseHeaders(403, -1);
      return;
    }
    run(
        definitionRepository,
        actionServices,
        inputSource,
        new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8),
        http);
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }
}
