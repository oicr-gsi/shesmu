package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ExtractionNodeGang extends ExtractionNode {
  private GangDefinition definition;
  private List<Target> elements;
  private final String gang;

  public ExtractionNodeGang(int line, int column, String gang) {
    super(line, column);
    this.gang = gang;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    if (predicate.test(Flavour.STREAM)) {
      definition.elements().map(GangElement::name).forEach(names::add);
    }
  }

  @Override
  public Stream<String> names() {
    return definition.elements().map(GangElement::name);
  }

  @Override
  public void render(OutputCollector outputCollector) {
    for (final var element : elements) {
      outputCollector.addValue(element.name(), r -> r.loadTarget(element), element.type());
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final var result =
        TypeUtils.matchGang(
            line(),
            column(),
            defs,
            definition,
            (input, expectedType, dropIfEmpty) -> input,
            errorHandler);
    result.ifPresent(x -> elements = x);
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var foundGang =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(gang))
            .findAny();
    foundGang.ifPresentOrElse(
        d -> definition = d,
        () ->
            errorHandler.accept(
                String.format("%d:%d: Unknown gang “%s”.", line(), column(), gang)));
    return foundGang.isPresent();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler, OutputFormat outputFormat) {
    return definition
        .elements()
        .allMatch(
            e -> {
              final var allowed = outputFormat.isAllowedType(e.type());
              if (!allowed) {
                errorHandler.accept(
                    String.format(
                        "%d:%d: Gang “%s” has element “%s” which has type %s not supported by %s.",
                        line(), column(), gang, e.name(), e.type().name(), outputFormat));
              }
              return allowed;
            });
  }
}
