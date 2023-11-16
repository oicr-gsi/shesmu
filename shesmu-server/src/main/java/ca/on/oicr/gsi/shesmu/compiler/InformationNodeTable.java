package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InformationNodeTable extends InformationNodeBaseRepeat {
  public static final class ColumnNode {
    private final List<DisplayNode> contents;
    private final String header;

    public ColumnNode(String header, List<DisplayNode> contents) {
      this.header = header;
      this.contents = contents;
    }

    public String header() {
      try {
        return RuntimeSupport.MAPPER.writeValueAsString(header);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    public String renderEcma(EcmaScriptRenderer renderer) {
      return contents.stream()
          .map(c -> c.renderEcma(renderer))
          .collect(Collectors.joining(", ", "[", "]"));
    }

    public boolean resolve(NameDefinitions collectorName, Consumer<String> errorHandler) {
      return contents.stream().filter(c -> c.resolve(collectorName, errorHandler)).count()
          == contents.size();
    }

    public boolean resolveDefinitions(
        ExpressionCompilerServices expressionCompilerServices,
        DefinitionRepository nativeDefinitions,
        Consumer<String> errorHandler) {
      return contents.stream()
              .filter(
                  c ->
                      c.resolveDefinitions(
                          expressionCompilerServices, nativeDefinitions, errorHandler))
              .count()
          == contents.size();
    }

    public boolean typeCheck(Consumer<String> errorHandler) {
      return contents.stream().filter(c -> c.typeCheck(errorHandler)).count() == contents.size();
    }
  }

  private final List<ColumnNode> columns;

  public InformationNodeTable(
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      List<ColumnNode> columns) {
    super(name, source, transforms);
    this.columns = columns;
  }

  @Override
  protected String renderBlock(EcmaScriptRenderer renderer, String data) {
    return String.format(
        "{type: \"table\", data: %s, headers: %s}",
        data, columns.stream().map(ColumnNode::header).collect(Collectors.joining(", ", "[", "]")));
  }

  @Override
  public String renderRow(EcmaScriptRenderer renderer) {
    return columns.stream()
        .map(collector -> collector.renderEcma(renderer))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  protected boolean resolveTerminal(
      NameDefinitions parentName, NameDefinitions collectorName, Consumer<String> errorHandler) {
    return columns.stream()
            .filter(collector -> collector.resolve(collectorName, errorHandler))
            .count()
        == columns.size();
  }

  @Override
  protected boolean resolveTerminalDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return columns.stream()
            .filter(
                collector ->
                    collector.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == columns.size();
  }

  @Override
  protected boolean typeCheckTerminal(Consumer<String> errorHandler) {
    return columns.stream().filter(collector -> collector.typeCheck(errorHandler)).count()
        == columns.size();
  }
}
