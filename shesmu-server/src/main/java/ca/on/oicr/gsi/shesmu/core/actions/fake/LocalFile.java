package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Optional;

public class LocalFile extends JsonPluginFile<ObjectNode[]> {

  private final Definer<LocalFile> definer;

  public LocalFile(Path fileName, String instanceName, Definer<LocalFile> definer) {
    super(fileName, instanceName, FakeAction.MAPPER, ObjectNode[].class);
    this.definer = definer;
  }

  public void configuration(SectionRenderer renderer) {}

  @Override
  protected Optional<Integer> update(ObjectNode[] configuration) {
    definer.clearActions();
    for (final var obj : configuration) {
      var name = obj.get("name").asString();
      definer.defineAction(
          name,
          "Fake version of: " + obj.get("description").asString(),
          FakeAction.class,
          () -> new FakeAction(name),
          Utils.stream(obj.path("parameters").values())
              .map(
                  p ->
                      new JsonParameter<>(
                          p.get("name").asString(),
                          p.get("required").asBoolean(),
                          Imyhat.parse(p.get("type").asString()))));
    }
    return Optional.empty();
  }
}
