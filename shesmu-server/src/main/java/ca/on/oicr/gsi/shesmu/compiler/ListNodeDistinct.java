package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ListNodeDistinct extends ListNode {
  public ListNodeDistinct(int line, int column) {
    super(line, column);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  public LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    builder.distinct();
    return name;
  }

  @Override
  public EcmaLoadableConstructor render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.distinct();
    return name;
  }

  @Override
  public Optional<DestructuredArgumentNode> resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    return Optional.of(name);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return Optional.of(incoming);
  }
}
