package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardNodeForm extends WizardNode {
  private final List<InformationNode> information;
  private final List<FormNode> entries;
  private final WizardNode next;
  private final int line;
  private final int column;

  public WizardNodeForm(
      int line,
      int column,
      List<InformationNode> information,
      List<FormNode> entries,
      WizardNode next) {
    this.line = line;
    this.column = column;
    this.information = information;
    this.entries = entries;
    this.next = next;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer, EcmaLoadableConstructor name) {
    final String nextFunction =
        next.renderEcma(
            renderer,
            base ->
                Stream.concat(
                    name.create(base),
                    entries
                        .stream()
                        .map(
                            n ->
                                new EcmaLoadableValue() {
                                  @Override
                                  public String name() {
                                    return n.name();
                                  }

                                  @Override
                                  public String apply(EcmaScriptRenderer renderer) {
                                    return base.apply(renderer) + "." + n.name();
                                  }
                                })));
    return renderer.newConst(
        renderer.lambda(
            1,
            (r, a) -> {
              name.create(rr -> a.apply(0)).forEach(r::define);
              return String.format(
                  "{information: %s, then: {type: \"form\", parameters: %s, processor: %s}}",
                  information
                      .stream()
                      .map(i -> i.renderEcma(r))
                      .collect(Collectors.joining(", ", "[", "]")),
                  entries
                      .stream()
                      .map(
                          c -> {
                            try {
                              return RuntimeSupport.MAPPER.writeValueAsString(c.name())
                                  + ": "
                                  + c.renderEcma(r);
                            } catch (JsonProcessingException e) {
                              throw new RuntimeException(e);
                            }
                          })
                      .collect(Collectors.joining(",", "{", "}")),
                  nextFunction);
            }));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Map.Entry<String, Long> entry :
        entries
            .stream()
            .collect(Collectors.groupingBy(FormNode::name, Collectors.counting()))
            .entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Form value %s appears %d times.",
                line, column, entry.getKey(), entry.getValue()));
        ok = false;
      }
    }
    return ok
        & information.stream().filter(i -> i.resolve(defs, errorHandler)).count()
            == information.size()
        & entries.stream().filter(e -> e.resolve(defs, errorHandler)).count() == entries.size()
        & next.resolve(defs.bind(entries), errorHandler);
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
    return information
                .stream()
                .filter(
                    i ->
                        i.resolveDefinitions(
                            expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == information.size()
        & entries
                .stream()
                .filter(
                    e ->
                        e.resolveDefinitions(
                            expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == entries.size()
        & next.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return information.stream().filter(i -> i.typeCheck(errorHandler)).count() == information.size()
        & entries.stream().filter(e -> e.typeCheck(errorHandler)).count() == entries.size()
        & next.typeCheck(errorHandler);
  }
}