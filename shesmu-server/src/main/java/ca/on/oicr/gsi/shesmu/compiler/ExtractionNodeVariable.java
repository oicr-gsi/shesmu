package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ExtractionNodeVariable extends ExtractionNode {
  private final String name;
  private Target target = Target.BAD;

  public ExtractionNodeVariable(int line, int column, String name) {
    super(line, column);
    this.name = name;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    if (predicate.test(target.flavour())) {
      names.add(target.name());
    }
  }

  @Override
  public Stream<String> names() {
    return Stream.of(name);
  }

  @Override
  public void render(OutputCollector outputCollector) {
    outputCollector.addValue(name, r -> r.loadTarget(target), target.type());
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final var result = defs.get(name);
    result.ifPresentOrElse(
        t -> target = t,
        () ->
            errorHandler.accept(
                String.format("%d:%d: Unknown name “%s”.", line(), column(), name)));
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler, OutputFormat outputFormat) {
    if (outputFormat.isAllowedType(target.type())) {
      return true;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Variable “%s” has type %s not supported by %s.",
              line(), column(), name, target.type().name(), outputFormat));
      return false;
    }
  }
}
