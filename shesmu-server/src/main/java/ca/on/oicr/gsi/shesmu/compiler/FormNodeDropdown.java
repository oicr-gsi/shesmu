package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FormNodeDropdown extends FormNode {

  private final DestructuredArgumentNode itemName;
  private final List<DisplayNode> label;
  private final DisplayNode itemLabel;
  private final String name;
  private Imyhat type = Imyhat.BAD;
  private final ExpressionNode values;

  public FormNodeDropdown(
      List<DisplayNode> label,
      String name,
      ExpressionNode values,
      DestructuredArgumentNode itemName,
      DisplayNode itemLabel) {
    this.label = label;
    this.itemName = itemName;
    this.itemLabel = itemLabel;
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
        "{label: %s, type: \"select-dynamic\", values: %s, labelMaker: %s}",
        label.stream().map(l -> l.renderEcma(renderer)).collect(Collectors.joining(", ", "[", "]")),
        values.renderEcma(renderer),
        renderer.lambda(
            1,
            (r, a) -> {
              itemName.renderEcma(rr -> a.apply(0)).forEach(r::define);
              return itemLabel.renderEcma(r);
            }));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.resolve(defs, errorHandler)).count() == label.size()
        & itemLabel.resolve(defs.bind(itemName), errorHandler)
        & values.resolve(defs, errorHandler);
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
            == label.size()
        & itemName.resolve(expressionCompilerServices, errorHandler)
        & itemLabel.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler)
        & values.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public Imyhat type() {
    return type;
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    final boolean ok =
        label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size();
    if (values.typeCheck(errorHandler)) {
      if (values.type() instanceof ListImyhat) {
        type = ((ListImyhat) values.type()).inner();
        return itemName.typeCheck(type, errorHandler) && itemLabel.typeCheck(errorHandler) && ok;
      } else {
        values.typeError("list", values.type(), errorHandler);
      }
    }
    return false;
  }
}
