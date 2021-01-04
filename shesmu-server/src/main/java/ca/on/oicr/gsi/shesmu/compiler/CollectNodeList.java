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

public class CollectNodeList extends CollectNode {

  private List<String> definedNames;
  private final ExpressionNode expression;

  public CollectNodeList(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    expression.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Renderer renderer =
        builder.map(
            line(),
            column(),
            name,
            expression.type(),
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    expression.render(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
    builder.collect();
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, expression.type(), expression::renderEcma);
    return String.format(
        "$runtime.setNew(%s, (a, b) => %s)",
        builder.finish(), expression.type().apply(EcmaScriptRenderer.COMPARATOR));
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final boolean ok = expression.resolve(defs.bind(name), errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return expression.type().asList();
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
