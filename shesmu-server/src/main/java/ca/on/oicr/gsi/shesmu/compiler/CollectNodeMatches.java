package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CollectNodeMatches extends CollectNode {

  private final Match matchType;
  private List<String> definedNames;
  private final ExpressionNode selector;

  public CollectNodeMatches(int line, int column, Match matchType, ExpressionNode selector) {
    super(line, column);
    this.matchType = matchType;
    this.selector = selector;
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    selector.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    selector.collectPlugins(pluginFileNames);
  }

  @Override
  public final void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    selector.collectFreeVariables(freeVariables, Flavour::needsCapture);
    freeVariables.removeAll(definedNames);
    final Renderer renderer =
        builder.match(
            line(),
            column(),
            matchType,
            name,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    selector.render(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  @Override
  public final boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final boolean ok = selector.resolve(defs.bind(name), errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return selector.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public final Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    if (selector.typeCheck(errorHandler)) {
      if (!selector.type().isSame(Imyhat.BOOLEAN)) {
        errorHandler.accept(
            String.format(
                "%d:%d: Boolean value expected in %s, but got %s.",
                line(), column(), matchType.syntax(), selector.type().name()));
        return false;
      }
      return true;
    }
    return false;
  }
}
