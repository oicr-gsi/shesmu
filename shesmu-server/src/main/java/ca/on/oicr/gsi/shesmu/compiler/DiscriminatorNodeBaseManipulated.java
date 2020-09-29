package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class DiscriminatorNodeBaseManipulated extends DiscriminatorNode {
  private Imyhat innerType = Imyhat.BAD;
  private final ExpressionNode expression;
  private final DestructuredArgumentNode name;

  public DiscriminatorNodeBaseManipulated(
      ExpressionNode expression, DestructuredArgumentNode name) {
    this.expression = expression;
    this.name = name;
    name.setFlavour(Target.Flavour.STREAM);
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  protected abstract Imyhat convertType(Imyhat original, Consumer<String> errorHandler);

  @Override
  public final Stream<VariableInformation> dashboard() {
    final Set<String> freeStreamVariables = new HashSet<>();
    expression.collectFreeVariables(freeStreamVariables, Target.Flavour::isStream);
    return name.targets()
        .map(
            target ->
                new VariableInformation(
                    target.name(),
                    expression.type(),
                    freeStreamVariables.stream(),
                    VariableInformation.Behaviour.DEFINITION_BY));
  }

  @Override
  public void render(RegroupVariablesBuilder builder) {
    builder.addKeys(
        name,
        innerType.apply(TypeUtils.TO_ASM),
        renderer -> {
          expression.render(renderer);
          render(renderer);
        });
  }

  protected abstract void render(Renderer renderer);

  @Override
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = expression.resolve(defs, errorHandler);
    if (ok) {
      final Set<String> freeStreamVariables = new HashSet<>();
      expression.collectFreeVariables(freeStreamVariables, Target.Flavour::isStream);
      if (freeStreamVariables.isEmpty()) {
        errorHandler.accept(
            String.format(
                "%d:%d: New variable in By does not use any stream variables.",
                expression.line(), expression.column()));
        ok = false;
      }
    }
    switch (name.checkWildcard(errorHandler)) {
      case NONE:
        return ok;
      case HAS_WILDCARD:
        errorHandler.accept(
            String.format(
                "%d:%d: Wildcard not permitted in “By”.", expression.line(), expression.column()));
        return false;
      case BAD:
        return false;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public final Stream<DefinedTarget> targets() {
    return name.targets();
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    innerType = convertType(expression.type(), errorHandler);
    if (innerType.isBad()) {
      return false;
    } else {
      return name.typeCheck(innerType, errorHandler);
    }
  }
}
