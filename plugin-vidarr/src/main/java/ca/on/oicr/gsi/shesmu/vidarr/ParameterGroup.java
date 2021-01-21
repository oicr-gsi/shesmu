package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ParameterGroup implements Comparable<ParameterGroup> {

  static <T> Optional<CustomActionParameter<SubmitAction>> create(
      String groupName,
      BiConsumer<SubmitWorkflowRequest, ObjectNode> writer,
      Map<String, T> parameters,
      Function<T, Imyhat> converter) {
    final var handlers =
        parameters.entrySet().stream()
            .map(entry -> new ParameterGroup(entry.getKey(), converter.apply(entry.getValue())))
            .sorted()
            .collect(Collectors.toList());

    if (handlers.stream().anyMatch(h -> h.type.isBad())) {
      return Optional.empty();
    }
    return Optional.of(
        new CustomActionParameter<>(
            groupName, true, new ObjectImyhat(handlers.stream().map(ParameterGroup::objectField))) {
          @Override
          public void store(SubmitAction action, Object value) {
            final var tuple = (Tuple) value;
            final var object = VidarrPlugin.MAPPER.createObjectNode();
            writer.accept(action.request, object);
            for (var index = 0; index < handlers.size(); index++) {
              handlers.get(index).store(object, tuple.get(index));
            }
          }
        });
  }

  final String name;
  final Imyhat type;

  ParameterGroup(String name, Imyhat type) {
    this.type = type;
    this.name = name;
  }

  @Override
  public int compareTo(ParameterGroup metadataParameter) {
    return name.compareTo(metadataParameter.name);
  }

  Pair<String, Imyhat> objectField() {
    return new Pair<>(VidarrPlugin.sanitise(name), type);
  }

  void store(ObjectNode output, Object value) {
    type.accept(new PackJsonObject(output, name), value);
  }
}
