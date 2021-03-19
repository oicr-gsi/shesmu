package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
      var name = obj.get("name").asText();
      definer.defineAction(
          name,
          "Fake version of: " + obj.get("description").asText(),
          FakeAction.class,
          () -> new FakeAction(name),
          Utils.stream(obj.get("parameters").elements())
              .map(
                  p ->
                      new JsonParameter<>(
                          p.get("name").asText(),
                          p.get("required").asBoolean(),
                          Imyhat.parse(p.get("type").asText()))));
    }
    return Optional.empty();
  }
}
