package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeLet extends WizardNode {
  private final List<Pair<DestructuredArgumentNode, ExpressionNode>> entries;
  private final WizardNode next;
  private final int line;
  private final int column;

  public WizardNodeLet(
      int line,
      int column,
      List<Pair<DestructuredArgumentNode, ExpressionNode>> entries,
      WizardNode next) {
    this.line = line;
    this.column = column;
    this.entries = entries;
    this.next = next;
    entries.forEach(e -> e.first().setFlavour(Flavour.LAMBDA));
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final List<EcmaLoadableValue> values = new ArrayList<>();
    for (final Pair<DestructuredArgumentNode, ExpressionNode> entry : entries) {
      entry
          .first()
          .renderEcma(renderer.newConst(entry.second().renderEcma(renderer)))
          .forEach(values::add);
    }
    values.forEach(renderer::define);
    return next.renderEcma(renderer);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Map.Entry<String, Long> entry :
        entries.stream()
            .flatMap(p -> p.first().targets())
            .collect(Collectors.groupingBy(Target::name, Collectors.counting()))
            .entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Fetch value %s appears %d times.",
                line, column, entry.getKey(), entry.getValue()));
        ok = false;
      }
    }
    return ok
        & entries.stream()
                .filter(
                    e -> {
                      switch (e.first().checkWildcard(errorHandler)) {
                        case BAD:
                          return false;
                        case NONE:
                          return e.second().resolve(defs, errorHandler);
                        case HAS_WILDCARD:
                          errorHandler.accept(
                              String.format(
                                  "%d:%d: Wildcard not allowed in assignment.",
                                  e.second().line(), e.second().column()));
                          return false;
                      }
                      throw new IllegalStateException();
                    })
                .count()
            == entries.size()
        & next.resolve(
            defs.bind(
                entries.stream().flatMap(p -> p.first().targets()).collect(Collectors.toList())),
            errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return next.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return entries.stream()
                .filter(
                    e ->
                        e.first().resolve(expressionCompilerServices, errorHandler)
                            & e.second()
                                .resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == entries.size()
        & next.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return entries.stream()
                .filter(
                    e ->
                        e.second().typeCheck(errorHandler)
                            && e.first().typeCheck(e.second().type(), errorHandler))
                .count()
            == entries.size()
        & next.typeCheck(errorHandler);
  }
}
