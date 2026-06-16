package ca.on.oicr.gsi.shesmu.vidarr;

import tools.jackson.databind.node.ObjectNode;

public interface EmptyObjectSetter {
  void set(VidarrAction action, ObjectNode value);
}
