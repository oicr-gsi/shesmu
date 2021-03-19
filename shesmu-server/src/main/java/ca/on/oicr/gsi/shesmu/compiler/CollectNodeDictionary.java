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

public class CollectNodeDictionary extends CollectNode {

  private List<String> definedNames;
  private final ExpressionNode key;
  private final ExpressionNode value;

  public CollectNodeDictionary(int line, int column, ExpressionNode key, ExpressionNode value) {
    super(line, column);
    this.key = key;
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    key.collectFreeVariables(names, predicate);
    value.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    key.collectPlugins(pluginFileNames);
    value.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    key.collectFreeVariables(freeVariables, Flavour::needsCapture);
    value.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final var renderer =
        builder.dictionary(
            line(),
            column(),
            name,
            key.type(),
            value.type(),
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.first().methodGen().visitCode();
    key.render(renderer.first());
    renderer.first().methodGen().returnValue();
    renderer.first().methodGen().visitMaxs(0, 0);
    renderer.first().methodGen().visitEnd();
    renderer.second().methodGen().visitCode();
    value.render(renderer.second());
    renderer.second().methodGen().returnValue();
    renderer.second().methodGen().visitMaxs(0, 0);
    renderer.second().methodGen().visitEnd();
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(
        name,
        Imyhat.tuple(key.type(), value.type()),
        r -> String.format("[%s, %s]", key.renderEcma(r), value.renderEcma(r)));
    return String.format(
        "$runtime.dictNew(%s, (a, b) => %s)",
        builder.finish(), key.type().apply(EcmaScriptRenderer.COMPARATOR));
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final var innerDefs = defs.bind(name);
    final var ok = key.resolve(innerDefs, errorHandler) & value.resolve(innerDefs, errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return key.resolveDefinitions(expressionCompilerServices, errorHandler)
        & value.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.dictionary(key.type(), value.type());
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return key.typeCheck(errorHandler) & value.typeCheck(errorHandler);
  }
}
