package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FormNodeSubset extends FormNode {

  private final List<DisplayNode> label;
  private final String name;
  private final ExpressionNode values;

  public FormNodeSubset(List<DisplayNode> label, String name, ExpressionNode values) {
    this.label = label;
    this.name = name;
    this.values = values;
  }

  @Override
  public Flavour flavour() {
    return Flavour.LAMBDA;
  }

  @Override
  public String name() {
    return name;
  }

  public void read() {
    // Don't really care
  }

  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{label: %s, type: \"subset\", values: %s}",
        label.stream().map(l -> l.renderEcma(renderer)).collect(Collectors.joining(", ", "[", "]")),
        values.renderEcma(renderer));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.resolve(defs, errorHandler)).count() == label.size()
        & values.resolve(defs, errorHandler);
  }

  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return label
                .stream()
                .filter(
                    l ->
                        l.resolveDefinitions(
                            expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == label.size()
        & values.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public Imyhat type() {
    return Imyhat.STRING.asList();
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = values.typeCheck(errorHandler);
    if (ok) {
      if (!Imyhat.STRING.asList().isAssignableFrom(values.type())) {
        values.typeError(Imyhat.STRING.asList(), values.type(), errorHandler);
        ok = false;
      }
    }
    return ok & label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size();
  }
}
