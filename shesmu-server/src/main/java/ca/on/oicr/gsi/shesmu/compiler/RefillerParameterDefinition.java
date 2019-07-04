package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public interface RefillerParameterDefinition {
  Imyhat type();

  String name();

  void render(Renderer renderer, int refillerLocal, int functionLocal);
}
