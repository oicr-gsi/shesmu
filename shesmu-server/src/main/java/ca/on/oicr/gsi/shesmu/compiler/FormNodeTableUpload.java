package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FormNodeTableUpload extends FormNode {

  private final int column;
  private final List<String> columns;
  private final List<DisplayNode> label;
  private final int line;
  private final String name;

  public FormNodeTableUpload(
      int line, int column, List<DisplayNode> label, String name, List<String> columns) {
    this.line = line;
    this.column = column;
    this.label = label;
    this.name = name;
    this.columns = columns;
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
        "{label: %s, type: \"upload-table\", columns: %s}",
        label.stream().map(l -> l.renderEcma(renderer)).collect(Collectors.joining(", ", "[", "]")),
        columns.stream()
            .map(
                l -> {
                  try {
                    return RuntimeSupport.MAPPER.writeValueAsString(l);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.joining(", ", "[", "]")));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = true;
    final Map<String, Long> duplicates =
        columns.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    for (final Map.Entry<String, Long> entry : duplicates.entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Column %s appears %d times.",
                line, column, entry.getKey(), entry.getValue()));
        ok = false;
      }
    }
    return label.stream().filter(l -> l.resolve(defs, errorHandler)).count() == label.size() && ok;
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
    return new ObjectImyhat(columns.stream().map(c -> new Pair<>(c, Imyhat.STRING))).asList();
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    return label.stream().filter(l -> l.typeCheck(errorHandler)).count() == label.size();
  }
}
