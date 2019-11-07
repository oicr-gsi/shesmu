package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExpressionNodeBlock extends ExpressionNode {
  private final List<Pair<DestructuredArgumentNode, ExpressionNode>> definitions;
  private final ExpressionNode result;

  public ExpressionNodeBlock(
      int line,
      int column,
      List<Pair<DestructuredArgumentNode, ExpressionNode>> definitions,
      ExpressionNode result) {
    super(line, column);
    this.definitions = definitions;
    this.result = result;
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      definition.first().setFlavour(Target.Flavour.LAMBDA);
    }
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    final Set<String> removeNames = new TreeSet<>();
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      definition
          .first()
          .targets()
          .filter(t -> predicate.test(t.flavour()))
          .map(Target::name)
          .filter(n -> !names.contains(n))
          .forEach(removeNames::add);
      definition.second().collectFreeVariables(names, predicate);
    }
    result.collectFreeVariables(names, predicate);
    names.removeAll(removeNames);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    result.collectPlugins(pluginFileNames);
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      definition.second().collectPlugins(pluginFileNames);
    }
  }

  @Override
  public void render(Renderer renderer) {
    Renderer current = renderer.duplicate();
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      definition.second().render(current);
      final int local = current.methodGen().newLocal(definition.second().type().apply(TO_ASM));
      current.methodGen().storeLocal(local);
      definition
          .first()
          .render(r -> r.methodGen().loadLocal(local))
          .forEach(c -> current.define(c.name(), c));
    }
    result.render(current);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    NameDefinitions current = defs;
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      if (!definition.second().resolve(current, errorHandler)) {
        return false;
      }
      current = current.bind(definition.first().targets().collect(Collectors.toList()));
    }
    return result.resolve(current, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return definitions
                .stream()
                .filter(
                    d -> !d.second().resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == 0
        & result.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return result.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    for (final Pair<DestructuredArgumentNode, ExpressionNode> definition : definitions) {
      if (!definition.second().typeCheck(errorHandler)) {
        return false;
      }
      if (!definition.first().typeCheck(definition.second().type(), errorHandler)) {
        return false;
      }
    }
    return result.typeCheck(errorHandler);
  }
}
