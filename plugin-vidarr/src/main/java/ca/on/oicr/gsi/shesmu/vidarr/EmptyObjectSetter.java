package ca.on.oicr.gsi.shesmu.vidarr;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface EmptyObjectSetter {
  void set(VidarrAction action, ObjectNode value);
}
