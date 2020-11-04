package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FormNodeSelect extends FormNode {

  private final List<DisplayNode> label;
  private final String name;
  private final List<Pair<DisplayNode, ExpressionNode>> options;
  Imyhat result = Imyhat.BAD;

  public FormNodeSelect(
      List<DisplayNode> label, String name, List<Pair<DisplayNode, ExpressionNode>> options) {
    this.label = label;
    this.name = name;
    this.options = options;
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
        "{label: %s, type: \"select\", items: %s}",
        label.stream().map(l -> l.renderEcma(renderer)).collect(Collectors.joining(", ", "[", "]")),
        options
            .stream()
            .map(
                p ->
                    String.format(
                        "[%s, %s]",
                        p.first().renderEcma(renderer), p.second().renderEcma(renderer)))
            .collect(Collectors.joining(", ", "[", "]")));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.resolve(defs, errorHandler)).count() == label.size()
        & options
                .stream()
                .filter(
                    p ->
                        p.first().resolve(defs, errorHandler)
                            & p.second().resolve(defs, errorHandler))
                .count()
            == options.size();
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
        & options
                .stream()
                .filter(
                    p ->
                        p.first()
                                .resolveDefinitions(
                                    expressionCompilerServices, nativeDefinitions, errorHandler)
                            & p.second()
                                .resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == options.size();
  }

  public Imyhat type() {
    return result;
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        options.stream().filter(p -> p.second().typeCheck(errorHandler)).count() == options.size();
    if (ok) {
      result = options.get(0).second().type();
      for (int i = 1; i < options.size(); i++) {
        final Imyhat expressionType = options.get(i).second().type();
        if (result.isSame(expressionType)) {
          result = result.unify(expressionType);
        } else {
          options.get(i).second().typeError(result, expressionType, errorHandler);
          ok = false;
        }
      }
    }
    return ok
        & label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size()
        & options.stream().filter(p -> p.first().typeCheck(errorHandler)).count() == options.size();
  }
}
