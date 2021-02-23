package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ParameterGroup implements Comparable<ParameterGroup> {

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
