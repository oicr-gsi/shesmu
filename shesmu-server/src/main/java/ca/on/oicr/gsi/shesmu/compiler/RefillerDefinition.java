package ca.on.oicr.gsi.shesmu.compiler;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface RefillerDefinition {
  Path filename();

  String name();

  String description();

  Stream<RefillerParameterDefinition> parameters();

  void render(Renderer renderer);
}
