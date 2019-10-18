package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DiscriminatorNodeRename extends DiscriminatorNode {

  private final ExpressionNode expression;
  private final DefinedTarget target;

  public DiscriminatorNodeRename(int line, int column, String name, ExpressionNode expression) {
    this.expression = expression;
    target =
        new DefinedTarget() {
          @Override
          public int column() {
            return column;
          }

          @Override
          public int line() {
            return line;
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public Flavour flavour() {
            return Flavour.STREAM;
          }

          @Override
          public Imyhat type() {
            return expression.type();
          }
        };
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public Stream<VariableInformation> dashboard() {
    final Set<String> freeStreamVariables = new HashSet<>();
    expression.collectFreeVariables(freeStreamVariables, Target.Flavour::isStream);
    return Stream.of(
        new VariableInformation(
            target.name(),
            expression.type(),
            freeStreamVariables.stream(),
            Behaviour.DEFINITION_BY));
  }

  @Override
  public void render(RegroupVariablesBuilder builder) {
    builder.addKey(expression.type().apply(TypeUtils.TO_ASM), target.name(), expression::render);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = expression.resolve(defs, errorHandler);
    if (ok) {
      final Set<String> freeStreamVariables = new HashSet<>();
      expression.collectFreeVariables(freeStreamVariables, Target.Flavour::isStream);
      if (freeStreamVariables.isEmpty()) {
        errorHandler.accept(
            String.format(
                "%d:%d: New variable “%s” in By does not use any stream variables.",
                target.line(), target.column(), target.name()));
        ok = false;
      }
    }
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return Stream.of(target);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
