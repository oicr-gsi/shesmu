package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter.ActionFilterNode;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.SourceOliveLocation;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InformationNodeActions extends InformationNode {

  public static String renderEcmaForFilter(
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter,
      EcmaScriptRenderer renderer) {
    return filter
        .generate(
            s -> Optional.empty(),
            new ActionFilterBuilder<
                String,
                InformationParameterNode<ActionState>,
                InformationParameterNode<String>,
                InformationParameterNode<Instant>,
                InformationParameterNode<Long>>() {
              @Override
              public String added(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return String.format(
                    "{type: \"added\", start: %s, end: %s}",
                    start.map(s -> s.renderEcma(renderer)),
                    end.map(e -> e.renderEcma(renderer)).orElse("null"));
              }

              @Override
              public String addedAgo(InformationParameterNode<Long> offset) {
                return String.format(
                    "{type: \"addedago\", offset: %s}", offset.renderEcma(renderer));
              }

              @Override
              public String and(Stream<String> filters) {
                return String.format(
                    "{type: \"and\", filters: %s}",
                    filters.collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String checked(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return String.format(
                    "{type: \"checked\", start: %s, end: %s}",
                    start.map(s -> s.renderEcma(renderer)),
                    end.map(e -> e.renderEcma(renderer)).orElse("null"));
              }

              @Override
              public String checkedAgo(InformationParameterNode<Long> offset) {
                return String.format(
                    "{type: \"checkedago\", offset: %s}", offset.renderEcma(renderer));
              }

              @Override
              public String external(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return String.format(
                    "{type: \"external\", start: %s, end: %s}",
                    start.map(s -> s.renderEcma(renderer)),
                    end.map(e -> e.renderEcma(renderer)).orElse("null"));
              }

              @Override
              public String externalAgo(InformationParameterNode<Long> offset) {
                return String.format(
                    "{type: \"externalago\", offset: %s}", offset.renderEcma(renderer));
              }

              @Override
              public String fromFile(Stream<InformationParameterNode<String>> files) {
                return String.format(
                    "{type: \"sourcefile\", files: %s.flat(1)}",
                    files
                        .map(f -> f.renderEcma(renderer))
                        .collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String fromJson(ActionFilter actionFilter) {
                try {
                  return RuntimeSupport.MAPPER.writeValueAsString(actionFilter);
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }

              @Override
              public String fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return String.format(
                    "{type: \"sourcelocation\", locations: %s.flat(1)}",
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
              public String ids(List<InformationParameterNode<String>> ids) {
                return String.format(
                    "{type: \"id\", ids: %s.flat(1)}",
                    ids.stream()
                        .map(i -> i.renderEcma(renderer))
                        .collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String isState(Stream<InformationParameterNode<ActionState>> states) {
                return String.format(
                    "{type: \"status\", states: %s.flat(1)}",
                    states
                        .map(s -> s.renderEcma(renderer))
                        .collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String negate(String filter) {
                return String.format("{...%1$s, negate: !%1$s.negate}", renderer.newConst(filter));
              }

              @Override
              public String or(Stream<String> filters) {
                return String.format(
                    "{type: \"or\", filters: %s}",
                    filters.collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String statusChanged(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return String.format(
                    "{type: \"statuschanged\", start: %s, end: %s}",
                    start.map(s -> s.renderEcma(renderer)),
                    end.map(e -> e.renderEcma(renderer)).orElse("null"));
              }

              @Override
              public String statusChangedAgo(InformationParameterNode<Long> offset) {
                return String.format(
                    "{type: \"statuschangedago\", offset: %s}", offset.renderEcma(renderer));
              }

              @Override
              public String tag(Pattern pattern) {
                try {
                  return String.format(
                      "{type: \"tag-regex\", matchCase: %s, pattern: %s}",
                      (((pattern.flags() & Pattern.CASE_INSENSITIVE)) == 0),
                      RuntimeSupport.MAPPER.writeValueAsString(pattern.pattern()));
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }

              @Override
              public String tags(Stream<InformationParameterNode<String>> tags) {
                return String.format(
                    "{type: \"tag\", tags: %s.flat(1)}",
                    tags.map(t -> t.renderEcma(renderer))
                        .collect(Collectors.joining(", ", "[", "]")));
              }

              @Override
              public String textSearch(Pattern pattern) {
                try {
                  return String.format(
                      "{type: \"regex\", matchCase: %s, pattern: %s}",
                      (((pattern.flags() & Pattern.CASE_INSENSITIVE)) == 0),
                      RuntimeSupport.MAPPER.writeValueAsString(pattern.pattern()));
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }

              @Override
              public String textSearch(InformationParameterNode<String> text, boolean matchCase) {
                return String.format(
                    "{type: \"text\", matchCase: %s, text: %s}",
                    matchCase, text.renderEcma(renderer));
              }

              @Override
              public String type(Stream<InformationParameterNode<String>> types) {
                return String.format(
                    "{type: \"type\", types: %s.flat(1)}",
                    types
                        .map(t -> t.renderEcma(renderer))
                        .collect(Collectors.joining(", ", "[", "]")));
              }
            },
            ((line, column, errorMessage) -> {
              throw new IllegalStateException("Error during rendering: " + errorMessage);
            }))
        .get();
  }

  public static boolean resolveDefinitionsForFilter(
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter,
      ExpressionCompilerServices expressionCompilerServices,
      Consumer<String> errorHandler) {
    return filter
        .generate(
            name -> Optional.of(true),
            new ActionFilterBuilder<>() {
              @Override
              public Boolean added(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start
                        .map(s -> s.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true)
                    & end.map(e -> e.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true);
              }

              @Override
              public Boolean addedAgo(InformationParameterNode<Long> offset) {
                return offset.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean checked(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start
                        .map(s -> s.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true)
                    & end.map(e -> e.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true);
              }

              @Override
              public Boolean checkedAgo(InformationParameterNode<Long> offset) {
                return offset.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean external(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start
                        .map(s -> s.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true)
                    & end.map(e -> e.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true);
              }

              @Override
              public Boolean externalAgo(InformationParameterNode<Long> offset) {
                return offset.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean fromFile(Stream<InformationParameterNode<String>> files) {
                return files.allMatch(
                    f -> f.resolveDefinitions(expressionCompilerServices, errorHandler));
              }

              @Override
              public Boolean fromJson(ActionFilter actionFilter) {
                return true;
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean ids(List<InformationParameterNode<String>> ids) {
                return ids.stream()
                    .allMatch(i -> i.resolveDefinitions(expressionCompilerServices, errorHandler));
              }

              @Override
              public Boolean isState(Stream<InformationParameterNode<ActionState>> states) {
                return states.allMatch(
                    s -> s.resolveDefinitions(expressionCompilerServices, errorHandler));
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean statusChanged(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start
                        .map(s -> s.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true)
                    & end.map(e -> e.resolveDefinitions(expressionCompilerServices, errorHandler))
                        .orElse(true);
              }

              @Override
              public Boolean statusChangedAgo(InformationParameterNode<Long> offset) {
                return offset.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean tag(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean tags(Stream<InformationParameterNode<String>> tags) {
                return tags.allMatch(
                    t -> t.resolveDefinitions(expressionCompilerServices, errorHandler));
              }

              @Override
              public Boolean textSearch(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean textSearch(InformationParameterNode<String> text, boolean matchCase) {
                return text.resolveDefinitions(expressionCompilerServices, errorHandler);
              }

              @Override
              public Boolean type(Stream<InformationParameterNode<String>> types) {
                return types.allMatch(
                    t -> t.resolveDefinitions(expressionCompilerServices, errorHandler));
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }

  public static boolean resolveForFilter(
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    return filter
        .generate(
            name -> Optional.of(true),
            new ActionFilterBuilder<>() {
              @Override
              public Boolean added(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.resolve(defs, errorHandler)).orElse(true)
                    & end.map(e -> e.resolve(defs, errorHandler)).orElse(true);
              }

              @Override
              public Boolean addedAgo(InformationParameterNode<Long> offset) {
                return offset.resolve(defs, errorHandler);
              }

              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean checked(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.resolve(defs, errorHandler)).orElse(true)
                    & end.map(e -> e.resolve(defs, errorHandler)).orElse(true);
              }

              @Override
              public Boolean checkedAgo(InformationParameterNode<Long> offset) {
                return offset.resolve(defs, errorHandler);
              }

              @Override
              public Boolean external(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.resolve(defs, errorHandler)).orElse(true)
                    & end.map(e -> e.resolve(defs, errorHandler)).orElse(true);
              }

              @Override
              public Boolean externalAgo(InformationParameterNode<Long> offset) {
                return offset.resolve(defs, errorHandler);
              }

              @Override
              public Boolean fromFile(Stream<InformationParameterNode<String>> files) {
                return files.allMatch(f -> f.resolve(defs, errorHandler));
              }

              @Override
              public Boolean fromJson(ActionFilter actionFilter) {
                return true;
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean ids(List<InformationParameterNode<String>> ids) {
                return ids.stream().allMatch(i -> i.resolve(defs, errorHandler));
              }

              @Override
              public Boolean isState(Stream<InformationParameterNode<ActionState>> states) {
                return states.allMatch(s -> s.resolve(defs, errorHandler));
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean statusChanged(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.resolve(defs, errorHandler)).orElse(true)
                    & end.map(e -> e.resolve(defs, errorHandler)).orElse(true);
              }

              @Override
              public Boolean statusChangedAgo(InformationParameterNode<Long> offset) {
                return offset.resolve(defs, errorHandler);
              }

              @Override
              public Boolean tag(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean tags(Stream<InformationParameterNode<String>> tags) {
                return tags.allMatch(t -> t.resolve(defs, errorHandler));
              }

              @Override
              public Boolean textSearch(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean textSearch(InformationParameterNode<String> text, boolean matchCase) {
                return text.resolve(defs, errorHandler);
              }

              @Override
              public Boolean type(Stream<InformationParameterNode<String>> types) {
                return types.allMatch(t -> t.resolve(defs, errorHandler));
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }

  public static boolean typeCheckForFilter(
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter,
      Consumer<String> errorHandler) {
    return filter
        .generate(
            name -> Optional.of(true),
            new ActionFilterBuilder<>() {
              @Override
              public Boolean added(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.typeCheck(errorHandler)).orElse(true)
                    & end.map(e -> e.typeCheck(errorHandler)).orElse(true);
              }

              @Override
              public Boolean addedAgo(InformationParameterNode<Long> offset) {
                return offset.typeCheck(errorHandler);
              }

              @Override
              public Boolean and(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean checked(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.typeCheck(errorHandler)).orElse(true)
                    & end.map(e -> e.typeCheck(errorHandler)).orElse(true);
              }

              @Override
              public Boolean checkedAgo(InformationParameterNode<Long> offset) {
                return offset.typeCheck(errorHandler);
              }

              @Override
              public Boolean external(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.typeCheck(errorHandler)).orElse(true)
                    & end.map(e -> e.typeCheck(errorHandler)).orElse(true);
              }

              @Override
              public Boolean externalAgo(InformationParameterNode<Long> offset) {
                return offset.typeCheck(errorHandler);
              }

              @Override
              public Boolean fromFile(Stream<InformationParameterNode<String>> files) {
                return files.allMatch(f -> f.typeCheck(errorHandler));
              }

              @Override
              public Boolean fromJson(ActionFilter actionFilter) {
                return true;
              }

              @Override
              public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
                return true;
              }

              @Override
              public Boolean ids(List<InformationParameterNode<String>> ids) {
                return ids.stream().allMatch(i -> i.typeCheck(errorHandler));
              }

              @Override
              public Boolean isState(Stream<InformationParameterNode<ActionState>> states) {
                return states.allMatch(s -> s.typeCheck(errorHandler));
              }

              @Override
              public Boolean negate(Boolean filter) {
                return filter;
              }

              @Override
              public Boolean or(Stream<Boolean> filters) {
                return filters.allMatch(x -> x);
              }

              @Override
              public Boolean statusChanged(
                  Optional<InformationParameterNode<Instant>> start,
                  Optional<InformationParameterNode<Instant>> end) {
                return start.map(s -> s.typeCheck(errorHandler)).orElse(true)
                    & end.map(e -> e.typeCheck(errorHandler)).orElse(true);
              }

              @Override
              public Boolean statusChangedAgo(InformationParameterNode<Long> offset) {
                return offset.typeCheck(errorHandler);
              }

              @Override
              public Boolean tag(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean tags(Stream<InformationParameterNode<String>> tags) {
                return tags.allMatch(t -> t.typeCheck(errorHandler));
              }

              @Override
              public Boolean textSearch(Pattern pattern) {
                return true;
              }

              @Override
              public Boolean textSearch(InformationParameterNode<String> text, boolean matchCase) {
                return text.typeCheck(errorHandler);
              }

              @Override
              public Boolean type(Stream<InformationParameterNode<String>> types) {
                return types.allMatch(t -> t.typeCheck(errorHandler));
              }
            },
            ((line, column, errorMessage) ->
                errorHandler.accept(String.format("%d:%d: %s", line, column, errorMessage))))
        .orElse(false);
  }

  private final ActionFilter.ActionFilterNode<
          InformationParameterNode<ActionState>,
          InformationParameterNode<String>,
          InformationParameterNode<Instant>,
          InformationParameterNode<Long>>
      filter;

  public InformationNodeActions(
      ActionFilterNode<
              InformationParameterNode<ActionState>,
              InformationParameterNode<String>,
              InformationParameterNode<Instant>,
              InformationParameterNode<Long>>
          filter) {
    this.filter = filter;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format("{type: \"actions\", filter: %s}", renderEcmaForFilter(filter, renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return resolveForFilter(filter, defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return resolveDefinitionsForFilter(filter, expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return typeCheckForFilter(filter, errorHandler);
  }
}
