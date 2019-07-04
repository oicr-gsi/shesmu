package ca.on.oicr.gsi.shesmu.compiler;

public interface RefillerParameterBuilder {
  LoadableValue[] captures();

  RefillerParameterDefinition parameter();

  void render(Renderer renderer);
}
