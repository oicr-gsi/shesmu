package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollectNodeTuple extends CollectNode {

  private List<String> definedNames;
  private final ExpressionNode inner;
  private final int size;

  public CollectNodeTuple(int line, int column, int size, ExpressionNode inner) {
    super(line, column);
    this.size = size;
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    inner.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    inner.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    if (ordering == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: Items to Tuple are in random order. Results will not be reproducible. Sort first.",
              line(), column()));
      return false;
    }
    return true;
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, inner.type(), inner::renderEcma);
    return String.format(
        "(%1$s.length == %2$d) ? %1$s: null", builder.renderer().newConst(builder.finish()), size);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    inner.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final var renderer =
        builder.map(
            line(),
            column(),
            name,
            inner.type(),
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    inner.render(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
    builder.toTuple(size);
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return inner.resolve(defs.bind(name), errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.tuple(Collections.nCopies(size, inner.type()).toArray(Imyhat[]::new))
        .asOptional();
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return inner.typeCheck(errorHandler);
  }
}
