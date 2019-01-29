package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class ListNodeBaseRange extends ListNode {

  private final ExpressionNode expression;
  private Imyhat incoming;
  private String name;

  protected ListNodeBaseRange(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final String nextName() {
    return name;
  }

  @Override
  public final Imyhat nextType() {
    return incoming;
  }

  @Override
  public final Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot apply a range limit to an unordered stream.", line(), column()));
      return Ordering.BAD;
    }
    return previous;
  }

  @Override
  public final void render(JavaStreamBuilder builder) {
    render(builder, expression::render);
  }

  protected abstract void render(JavaStreamBuilder builder, Consumer<Renderer> expression);

  @Override
  public final Optional<String> resolve(
      String name, NameDefinitions defs, Consumer<String> errorHandler) {
    this.name = name;
    return expression.resolve(defs, errorHandler) ? Optional.of(name) : Optional.empty();
  }

  @Override
  public final boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    this.incoming = incoming;
    final boolean ok = expression.typeCheck(errorHandler);
    if (ok && !expression.type().isSame(Imyhat.INTEGER)) {
      expression.typeError(Imyhat.INTEGER.name(), expression.type(), errorHandler);
      return false;
    }
    return true;
  }
}
