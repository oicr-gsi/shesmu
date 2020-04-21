package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class LetArgumentNodeBaseExpression extends LetArgumentNode {
  private final ExpressionNode expression;
  private final DestructuredArgumentNode name;

  public LetArgumentNodeBaseExpression(DestructuredArgumentNode name, ExpressionNode expression) {
    super();
    this.name = name;
    this.expression = expression;
    name.setFlavour(Target.Flavour.STREAM);
  }

  @Override
  public final boolean blankCheck(Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Assignment in Let discards value.", expression.line(), expression.column()));
      return false;
    }
    return true;
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return name.checkWildcard(errorHandler);
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return this.name.handleUndefinedVariable(name);
  }

  protected abstract Consumer<Renderer> render(
      LetBuilder let, Imyhat type, Consumer<Renderer> loadLocal);

  @Override
  public final void render(LetBuilder let) {
    final Consumer<Renderer> loadLocal =
        let.createLocal(expression.type().apply(TO_ASM), expression::render);
    name.render(render(let, expression.type(), loadLocal))
        .forEach(value -> let.add(value.type(), value.name(), value));
  }

  @Override
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public final boolean resolveFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & name.resolve(expressionCompilerServices, errorHandler);
  }

  @Override
  public final Stream<Target> targets() {
    return name.targets();
  }

  protected abstract boolean typeCheck(
      int line,
      int column,
      Imyhat type,
      DestructuredArgumentNode name,
      Consumer<String> errorHandler);

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    return typeCheck(expression.line(), expression.column(), expression.type(), name, errorHandler);
  }
}
