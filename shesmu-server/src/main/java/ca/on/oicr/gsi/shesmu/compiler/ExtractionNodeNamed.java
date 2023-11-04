package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ExtractionNodeNamed extends ExtractionNode {
  private final String name;
  private final ExpressionNode value;

  public ExtractionNodeNamed(int line, int column, String name, ExpressionNode value) {
    super(line, column);
    this.name = name;
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    value.collectFreeVariables(names, predicate);
  }

  @Override
  public Stream<String> names() {
    return Stream.of(name);
  }

  @Override
  public void render(OutputCollector outputCollector) {
    outputCollector.addValue(name, value::render, value.type());
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return value.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return value.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler, OutputFormat outputFormat) {
    if (value.typeCheck(errorHandler)) {
      if (outputFormat.isAllowedType(value.type())) {
        return true;
      } else {
        errorHandler.accept(
            String.format(
                "%d:%d: Column “%s” has type %s not supported by %s.",
                line(), column(), name, value.type().name(), outputFormat));
        return false;
      }
    } else {
      return false;
    }
  }
}
