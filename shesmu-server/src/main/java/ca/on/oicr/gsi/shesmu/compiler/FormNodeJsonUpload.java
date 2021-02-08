package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FormNodeJsonUpload extends FormNode {

  private final List<DisplayNode> label;
  private final String name;

  public FormNodeJsonUpload(List<DisplayNode> label, String name) {
    this.label = label;
    this.name = name;
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
        "{label: %s, type: \"upload-json\"}",
        label.stream()
            .map(l -> l.renderEcma(renderer))
            .collect(Collectors.joining(", ", "[", "]")));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.resolve(defs, errorHandler)).count() == label.size();
  }

  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return label.stream()
            .filter(
                l ->
                    l.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == label.size();
  }

  public Imyhat type() {
    return Imyhat.JSON;
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size();
  }
}
