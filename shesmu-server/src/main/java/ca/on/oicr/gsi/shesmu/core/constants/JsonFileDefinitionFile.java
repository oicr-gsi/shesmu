package ca.on.oicr.gsi.shesmu.core.constants;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;

public final class JsonFileDefinitionFile extends JsonPluginFile<ObjectNode> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Pair<Imyhat, Object> convert(JsonNode value) {
    if (value.isBoolean()) {
      return new Pair<>(Imyhat.BOOLEAN, value.asBoolean());
    }
    if (value.isIntegralNumber()) {
      return new Pair<>(Imyhat.INTEGER, value.asLong());
    }
    if (value.isString()) {
      return new Pair<>(Imyhat.STRING, value.asString());
    }
    if (value.isArray()) {
      if (value.isEmpty()) {
        return null;
      }
      Imyhat type;
      Function<JsonNode, Object> converter;
      if (value.get(0).isBoolean()) {
        type = Imyhat.BOOLEAN;
        converter = JsonNode::asBoolean;
      } else if (value.get(0).isIntegralNumber()) {
        type = Imyhat.INTEGER;
        converter = JsonNode::asLong;
      } else if (value.get(0).isString()) {
        type = Imyhat.STRING;
        converter = JsonNode::asString;
      } else {
        return null;
      }
      final var set = type.newSet();
      final var iterator = value.values().iterator();
      while (iterator.hasNext()) {
        set.add(converter.apply(iterator.next()));
      }
      return new Pair<>(type.asList(), set);
    }
    return null;
  }

  private final Set<String> badKeys = new ConcurrentSkipListSet<>();

  private final Definer<JsonFileDefinitionFile> definer;

  public JsonFileDefinitionFile(
      Path fileName, String instanceName, Definer<JsonFileDefinitionFile> definer) {
    super(fileName, instanceName, MAPPER, ObjectNode.class);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    if (!badKeys.isEmpty()) {
      renderer.line("Bad keys", String.join(", ", badKeys));
    }
  }

  @Override
  protected Optional<Integer> update(ObjectNode node) {
    final var description = String.format("User-defined value specified in %s.", fileName());
    definer.clearConstants();
    badKeys.clear();
    var iterator = node.properties().iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();
      final var constant = convert(e.getValue());
      if (constant != null) {
        definer.defineConstant(e.getKey(), description, constant.first(), constant.second());
      } else {
        badKeys.add(e.getKey());
      }
    }
    return Optional.empty();
  }
}
