package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FormNodePaste extends FormNode {
  private final String name;
  private final int flags;
  private final String regex;

  public FormNodePaste(List<DisplayNode> label, String name, String regex, int flags) {
    this.label = label;
    this.name = name;
    this.regex = regex;
    this.flags = flags;
  }

  private final List<DisplayNode> label;

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
    // String.prototype.matchAll requires a global flag, so we always set it
    // https://tc39.es/ecma262/#sec-string.prototype.matchall
    return String.format(
        "{label: %s, type: \"paste\", regex: /%s/g%s}",
        label.stream().map(l -> l.renderEcma(renderer)).collect(Collectors.joining(", ", "[", "]")),
        regex,
        EcmaScriptRenderer.regexFlagsToString(flags));
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
    return Imyhat.STRING.asList();
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size();
  }
}
