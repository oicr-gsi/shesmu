package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.filter.AlertFilter.AlertFilterNode;
import ca.on.oicr.gsi.shesmu.plugin.filter.AlertFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.SourceOliveLocation;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InformationNodeAlerts extends InformationNode {
  private final AlertFilterNode<InformationParameterNode<String>> filter;

  public InformationNodeAlerts(AlertFilterNode<InformationParameterNode<String>> filter) {
    this.filter = filter;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{type: \"alerts\", filter: %s}",
        filter
            .generate(
                new AlertFilterBuilder<String, InformationParameterNode<String>>() {
                  @Override
                  public String and(Stream<String> filters) {
                    return String.format(
                        "{type: \"and\", filters: %s}",
                        filters.collect(Collectors.joining(", ", "[", "]")));
                  }

                  @Override
                  public String fromSourceLocation(Stream<SourceOliveLocation> locations) {
                    return String.format(
                        "{type: \"sourcelocation\", locations: %s}",
                        locations
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

                  @Override
                  public String hasLabelName(Pattern labelName) {
                    try {
                      return String.format(
                          "{type: \"has\", isRegex: true, name: %s}",
                          RuntimeSupport.MAPPER.writeValueAsString(labelName.pattern()));
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                  }

                  @Override
                  public String hasLabelName(InformationParameterNode<String> labelName) {
                    return String.format(
                        "{type: \"has\", isRegex: false, name: %s}",
                        labelName.renderEcma(renderer));
                  }

                  @Override
                  public String hasLabelValue(
                      InformationParameterNode<String> labelName, Pattern regex) {
                    try {
                      return String.format(
                          "{type: \"eq\", isRegex: true, name: %s, value: %s}",
                          labelName.renderEcma(renderer),
                          RuntimeSupport.MAPPER.writeValueAsString(regex.pattern()));
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                  }

                  @Override
                  public String hasLabelValue(
                      InformationParameterNode<String> labelName,
                      InformationParameterNode<String> labelValue) {
                    return String.format(
                        "{type: \"eq\", isRegex: false, name: %s, value: %s}",
                        labelName.renderEcma(renderer), labelValue.renderEcma(renderer));
                  }

                  @Override
                  public String isLive() {
                    return "{type: \"is_live\"}";
                  }

                  @Override
                  public String negate(String filter) {
                    return String.format(
                        "{...%1$s, negate: !%1$s.negate}", renderer.newConst(filter));
                  }

                  @Override
                  public String or(Stream<String> filters) {
                    return String.format(
                        "{type: \"or\", filters: %s}",
                        filters.collect(Collectors.joining(", ", "[", "]")));
                  }
                },
                ((line, column, errorMessage) -> {
                  throw new IllegalStateException();
                }))
            .get());
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return filter
        .generate(
            new AlertFilterBuilder<Boolean, InformationParameterNode<String>>() {
              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean hasLabelName(Pattern labelName) {
                return true;
              }

              @Override
              public Boolean hasLabelName(InformationParameterNode<String> labelName) {
                return labelName.resolve(defs, errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName, Pattern regex) {
                return labelName.resolve(defs, errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName,
                  InformationParameterNode<String> labelValue) {
                return labelName.resolve(defs, errorHandler)
                    & labelValue.resolve(defs, errorHandler);
              }

              @Override
              public Boolean isLive() {
                return true;
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return filter
        .generate(
            new AlertFilterBuilder<Boolean, InformationParameterNode<String>>() {
              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean hasLabelName(Pattern labelName) {
                return true;
              }

              @Override
              public Boolean hasLabelName(InformationParameterNode<String> labelName) {
                return labelName.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName, Pattern regex) {
                return labelName.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName,
                  InformationParameterNode<String> labelValue) {
                return labelName.resolveDefinitions(expressionCompilerServices, errorHandler)
                    & labelValue.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean isLive() {
                return true;
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return filter
        .generate(
            new AlertFilterBuilder<Boolean, InformationParameterNode<String>>() {
              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean hasLabelName(Pattern labelName) {
                return true;
              }

              @Override
              public Boolean hasLabelName(InformationParameterNode<String> labelName) {
                return labelName.typeCheck(errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName, Pattern regex) {
                return labelName.typeCheck(errorHandler);
              }

              @Override
              public Boolean hasLabelValue(
                  InformationParameterNode<String> labelName,
                  InformationParameterNode<String> labelValue) {
                return labelName.typeCheck(errorHandler) & labelValue.typeCheck(errorHandler);
              }

              @Override
              public Boolean isLive() {
                return true;
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }
}
