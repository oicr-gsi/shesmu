package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public final class SimulateRequest extends BaseSimulateRequest {

  public static class FakeActionParameter {
    boolean required;
    Imyhat type;

    public Imyhat getType() {
      return type;
    }

    public boolean isRequired() {
      return required;
    }

    public void setRequired(boolean required) {
      this.required = required;
    }

    public void setType(Imyhat type) {
      this.type = type;
    }
  }

  private boolean allowUnused;
  private boolean dryRun;
  private Map<String, Map<String, FakeActionParameter>> fakeActions = Map.of();
  private Map<String, Map<String, Imyhat>> fakeRefillers = Map.of();
  private String script;

  @Override
  protected boolean allowUnused() {
    return allowUnused;
  }

  @Override
  protected boolean dryRun() {
    return dryRun;
  }

  @Override
  protected Stream<FakeActionDefinition> fakeActions() {
    return fakeActions.entrySet().stream()
        .map(
            e ->
                new FakeActionDefinition(
                    e.getKey(),
                    "Provided in simulation request",
                    null,
                    e.getValue().entrySet().stream()
                        .map(
                            pe ->
                                new JsonActionParameterDefinition(
                                    pe.getKey(),
                                    pe.getValue().required,
                                    pe.getValue().getType()))));
  }

  @Override
  protected Stream<FakeRefillerDefinition> fakeRefillers() {
    return fakeRefillers.entrySet().stream()
        .map(
            e ->
                new FakeRefillerDefinition(
                    e.getKey(),
                    "Fake refiller",
                    null,
                    e.getValue().entrySet().stream()
                        .map(p -> new JsonRefillerParameterDefinition(p.getKey(), p.getValue()))));
  }

  public Map<String, Map<String, FakeActionParameter>> getFakeActions() {
    return fakeActions;
  }

  public Map<String, Map<String, Imyhat>> getFakeRefillers() {
    return fakeRefillers;
  }

  public String getScript() {
    return script;
  }

  public boolean isAllowUnused() {
    return allowUnused;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  @Override
  protected boolean keepNativeAction(String name) {
    return !fakeActions.containsKey(name);
  }

  public void run(
      DefinitionRepository definitionRepository,
      ActionServices actionServices,
      InputSource inputSource,
      HttpExchange http)
      throws IOException {
    run(definitionRepository, actionServices, inputSource, script, http);
  }

  public void setAllowUnused(boolean allowUnused) {
    this.allowUnused = allowUnused;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  public void setFakeActions(Map<String, Map<String, FakeActionParameter>> fakeActions) {
    this.fakeActions = fakeActions;
  }

  public void setFakeRefillers(Map<String, Map<String, Imyhat>> fakeRefillers) {
    this.fakeRefillers = fakeRefillers;
  }

  public void setScript(String script) {
    this.script = script;
  }
}
