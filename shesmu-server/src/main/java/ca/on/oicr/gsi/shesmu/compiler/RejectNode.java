package ca.on.oicr.gsi.shesmu.compiler;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface RejectNode {
  void collectFreeVariables(Set<String> freeVariables);

  void collectPlugins(Set<Path> pluginFileNames);

  void render(RootBuilder builder, Renderer renderer);

  Stream<LoadableValue> requiredCaptures(RootBuilder builder);

  NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler);

  boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  boolean typeCheck(Consumer<String> errorHandler);
}
