package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeFetch extends WizardNode {
  private final List<Pair<String, FetchNode>> entries;
  private final WizardNode next;
  private final int line;
  private final int column;

  public WizardNodeFetch(
      int line, int column, List<Pair<String, FetchNode>> entries, WizardNode next) {
    this.line = line;
    this.column = column;
    this.entries = entries;
    this.next = next;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {

    return String.format(
        "{information: [], then: {type: \"fetch\", parameters: %s, processor: %s}}",
        entries.stream()
            .map(
                c -> {
                  try {
                    return RuntimeSupport.MAPPER.writeValueAsString(c.first())
                        + ": "
                        + c.second().renderEcma(renderer);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.joining(",", "{", "}")),
        renderer.lambda(
            1,
            (r, a) -> {
              for (final var entry : entries) {
                r.define(
                    new EcmaLoadableValue() {
                      @Override
                      public String name() {
                        return entry.first();
                      }

                      @Override
                      public String get() {
                        return a.apply(0) + "." + entry.first();
                      }
                    });
              }
              return next.renderEcma(r);
            }));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    var ok = true;
    for (final var entry :
        entries.stream()
            .collect(Collectors.groupingBy(Pair::first, Collectors.counting()))
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
        & entries.stream().filter(e -> e.second().resolve(defs, errorHandler)).count()
            == entries.size()
        & next.resolve(
            defs.bind(
                entries.stream()
                    .map(
                        e ->
                            new Target() {
                              @Override
                              public Flavour flavour() {
                                return Flavour.LAMBDA;
                              }

                              @Override
                              public String name() {
                                return e.first();
                              }

                              @Override
                              public void read() {
                                // Don't care
                              }

                              @Override
                              public Imyhat type() {
                                return e.second().type();
                              }
                            })
                    .collect(Collectors.toList())),
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
                        e.second()
                            .resolveDefinitions(
                                expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == entries.size()
        & next.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return entries.stream().filter(e -> e.second().typeCheck(errorHandler)).count()
            == entries.size()
        & next.typeCheck(errorHandler);
  }
}
