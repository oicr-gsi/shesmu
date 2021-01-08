package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface RefillerDefinition {
  Path filename();

  String name();

  String description();

  Stream<RefillerParameterDefinition> parameters();

  void render(Renderer renderer);

  default SupplementaryInformation supplementaryInformation() {
    return Stream::empty;
  }
}
