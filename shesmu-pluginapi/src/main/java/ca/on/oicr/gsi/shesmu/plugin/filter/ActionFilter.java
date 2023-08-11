package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.Parser.RuleWithLiteral;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.DateFormatSymbols;
import java.time.*;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The JSON representation of filters for actions used by the front end */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ActionFilterAdded.class, name = "added"),
  @JsonSubTypes.Type(value = ActionFilterAddedAgo.class, name = "addedago"),
  @JsonSubTypes.Type(value = ActionFilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = ActionFilterChecked.class, name = "checked"),
  @JsonSubTypes.Type(value = ActionFilterCheckedAgo.class, name = "checkedago"),
  @JsonSubTypes.Type(value = ActionFilterCreated.class, name = "created"),
  @JsonSubTypes.Type(value = ActionFilterCreatedAgo.class, name = "createdago"),
  @JsonSubTypes.Type(value = ActionFilterExternal.class, name = "external"),
  @JsonSubTypes.Type(value = ActionFilterExternalAgo.class, name = "externalago"),
  @JsonSubTypes.Type(value = ActionFilterIds.class, name = "id"),
  @JsonSubTypes.Type(value = ActionFilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = ActionFilterOrphaned.class, name = "orphaned"),
  @JsonSubTypes.Type(value = ActionFilterRegex.class, name = "regex"),
  @JsonSubTypes.Type(value = ActionFilterSourceFile.class, name = "sourcefile"),
  @JsonSubTypes.Type(value = ActionFilterSourceLocation.class, name = "sourcelocation"),
  @JsonSubTypes.Type(value = ActionFilterStatus.class, name = "status"),
  @JsonSubTypes.Type(value = ActionFilterStatusChanged.class, name = "statuschanged"),
  @JsonSubTypes.Type(value = ActionFilterStatusChangedAgo.class, name = "statuschangedago"),
  @JsonSubTypes.Type(value = ActionFilterTag.class, name = "tag"),
  @JsonSubTypes.Type(value = ActionFilterTagRegex.class, name = "tag-regex"),
  @JsonSubTypes.Type(value = ActionFilterText.class, name = "text"),
  @JsonSubTypes.Type(value = ActionFilterType.class, name = "type")
})
public abstract class ActionFilter {

  private enum Comparison implements TagParser {
    EQUALS("=") {
      @Override
      public <T> Parser parse(Parser parser, Rule<T> rule, BiConsumer<Boolean, List<T>> output) {
        return parser.whitespace().then(rule, s -> output.accept(false, List.of(s))).whitespace();
      }
    },
    NOT_EQUALS("!=") {
      @Override
      public <T> Parser parse(Parser parser, Rule<T> rule, BiConsumer<Boolean, List<T>> output) {
        return parser.whitespace().then(rule, s -> output.accept(true, List.of(s))).whitespace();
      }
    },
    IN("in") {
      @Override
      public <T> Parser parse(Parser parser, Rule<T> rule, BiConsumer<Boolean, List<T>> output) {
        return parser
            .whitespace()
            .symbol("(")
            .whitespace()
            .<T>list(
                s -> output.accept(false, s),
                (p, o) -> p.whitespace().then(rule, o).whitespace(),
                ',')
            .whitespace()
            .symbol(")")
            .whitespace();
      }
    },
    NOT_IN("not") {
      @Override
      public <T> Parser parse(Parser parser, Rule<T> rule, BiConsumer<Boolean, List<T>> output) {
        return parser
            .whitespace()
            .keyword("in")
            .whitespace()
            .symbol("(")
            .whitespace()
            .<T>list(
                s -> output.accept(true, s),
                (p, o) -> p.whitespace().then(rule, o).whitespace(),
                ',')
            .whitespace()
            .symbol(")")
            .whitespace();
      }
    };
    private final String symbol;

    Comparison(String symbol) {
      this.symbol = symbol;
    }

    public abstract <T> Parser parse(
        Parser parser, Rule<T> rule, BiConsumer<Boolean, List<T>> output);

    public <T, S, I, O> Parser parseTag(
        Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output) {
      return parse(
          parser,
          rule,
          (negate, tags) ->
              output.accept(
                  new ActionFilterNode<>() {
                    @Override
                    public <F> Optional<F> generate(
                        Function<String, Optional<F>> existing,
                        ActionFilterBuilder<F, T, S, I, O> builder,
                        ErrorConsumer errorHandler) {
                      final var result = builder.tags(tags.stream());
                      return Optional.of(negate ? builder.negate(result) : result);
                    }
                  }));
    }
  }

  private enum TemporalType {
    after {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return parser
            .whitespace()
            .then(
                instantRule,
                instant ->
                    output.accept(
                        new ActionFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            return Optional.of(
                                temporalBuilder.range(
                                    builder, Optional.of(instant), Optional.empty()));
                          }
                        }))
            .whitespace();
      }
    },
    before {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return parser
            .whitespace()
            .then(
                instantRule,
                instant ->
                    output.accept(
                        new ActionFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            return Optional.of(
                                temporalBuilder.range(
                                    builder, Optional.empty(), Optional.of(instant)));
                          }
                        }))
            .whitespace();
      }
    },
    between {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        final var start = new AtomicReference<I>();
        final var end = new AtomicReference<I>();
        final var result =
            parser
                .whitespace()
                .then(instantRule, start::set)
                .whitespace()
                .keyword("to")
                .whitespace()
                .then(instantRule, end::set)
                .whitespace();
        if (result.isGood()) {
          output.accept(
              new ActionFilterNode<>() {
                @Override
                public <F> Optional<F> generate(
                    Function<String, Optional<F>> existing,
                    ActionFilterBuilder<F, T, S, I, O> builder,
                    ErrorConsumer errorHandler) {
                  return Optional.of(
                      temporalBuilder.range(
                          builder, Optional.of(start.get()), Optional.of(end.get())));
                }
              });
        }
        return result;
      }
    },

    last {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return parser
            .whitespace()
            .then(
                offsetRule,
                offset ->
                    output.accept(
                        new ActionFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            return Optional.of(temporalBuilder.ago(builder, offset));
                          }
                        }))
            .whitespace();
      }
    },
    outside {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        final var start = new AtomicReference<I>();
        final var end = new AtomicReference<I>();
        final var result =
            parser
                .whitespace()
                .then(instantRule, start::set)
                .whitespace()
                .keyword("to")
                .whitespace()
                .then(instantRule, end::set)
                .whitespace();
        if (result.isGood()) {
          output.accept(
              new ActionFilterNode<>() {
                @Override
                public <F> Optional<F> generate(
                    Function<String, Optional<F>> existing,
                    ActionFilterBuilder<F, T, S, I, O> builder,
                    ErrorConsumer errorHandler) {
                  return Optional.of(
                      builder.negate(
                          temporalBuilder.range(
                              builder, Optional.of(start.get()), Optional.of(end.get()))));
                }
              });
        }
        return result;
      }
    },
    prior {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          TemporalBuilder temporalBuilder,
          Rule<I> instantRule,
          Rule<O> offsetRule,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return parser
            .whitespace()
            .then(
                offsetRule,
                offset ->
                    output.accept(
                        new ActionFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            return Optional.of(
                                builder.negate(temporalBuilder.ago(builder, offset)));
                          }
                        }))
            .whitespace();
      }
    };

    public abstract <T, S, I, O> Parser parse(
        Parser parser,
        TemporalBuilder temporalBuilder,
        Rule<I> instantRule,
        Rule<O> offsetRule,
        Consumer<ActionFilterNode<T, S, I, O>> output);
  }

  private enum Variable {
    GENERATED {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return TemporalBuilder.parse(parser, BUILD_ADDED, instant, offset, output);
      }
    },
    CHECKED {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return TemporalBuilder.parse(parser, BUILD_CHECKED, instant, offset, output);
      }
    },
    CREATED {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return TemporalBuilder.parse(parser, BUILD_CREATED, instant, offset, output);
      }
    },
    EXTERNAL {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return TemporalBuilder.parse(parser, BUILD_EXTERNAL, instant, offset, output);
      }
    },
    FILE {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return strings(
            parser,
            (negate, files) ->
                output.accept(
                    new ActionFilterNode<>() {
                      @Override
                      public <F> Optional<F> generate(
                          Function<String, Optional<F>> existing,
                          ActionFilterBuilder<F, T, S, I, O> builder,
                          ErrorConsumer errorHandler) {
                        final var result = Optional.of(builder.fromFile(files.stream()));
                        return negate ? result.map(builder::negate) : result;
                      }
                    }),
            strings);
      }
    },
    ORPHANED {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        output.accept(
            new ActionFilterNode<>() {
              @Override
              public <F> Optional<F> generate(
                  Function<String, Optional<F>> existing,
                  ActionFilterBuilder<F, T, S, I, O> builder,
                  ErrorConsumer errorHandler) {
                return Optional.of(builder.orphaned());
              }
            });
        return parser.whitespace();
      }
    },
    SOURCE {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        final var matches = new AtomicReference<Pair<Boolean, List<SourceOliveLocation>>>();
        final var result = parser.whitespace().dispatch(SOURCE_MATCH, matches::set).whitespace();
        if (result.isGood()) {
          output.accept(
              new ActionFilterNode<>() {
                @Override
                public <F> Optional<F> generate(
                    Function<String, Optional<F>> existing,
                    ActionFilterBuilder<F, T, S, I, O> builder,
                    ErrorConsumer errorHandler) {
                  return Optional.of(builder.fromSourceLocation(matches.get().second().stream()));
                }
              });
        }
        return result;
      }
    },
    STATUS {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return strings(
            parser,
            (negate, states) ->
                output.accept(
                    new ActionFilterNode<>() {
                      @Override
                      public <F> Optional<F> generate(
                          Function<String, Optional<F>> existing,
                          ActionFilterBuilder<F, T, S, I, O> builder,
                          ErrorConsumer errorHandler) {
                        final var result = Optional.of(builder.isState(states.stream()));
                        return negate ? result.map(builder::negate) : result;
                      }
                    }),
            actionState);
      }
    },

    STATUS_CHANGED {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return TemporalBuilder.parse(parser, BUILD_STATUS_CHANGED, instant, offset, output);
      }
    },
    TAG {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        final var tag = new AtomicReference<TagParser>();
        final var result = parser.whitespace().dispatch(TAG_MATCHER, tag::set).whitespace();
        if (result.isGood()) {
          return tag.get().parseTag(result, strings, output).whitespace();
        }
        return result;
      }
    },
    TEXT {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        final var text = new AtomicReference<TextParser>();
        final var result = parser.whitespace().dispatch(TEXT_MATCH, text::set).whitespace();
        if (result.isGood()) {
          return text.get().parseText(result, string, output).whitespace();
        }
        return result;
      }
    },
    TYPE {
      @Override
      public <T, S, I, O> Parser parse(
          Parser parser,
          Rule<T> actionState,
          Rule<S> string,
          Rule<S> strings,
          Rule<I> instant,
          Rule<O> offset,
          Consumer<ActionFilterNode<T, S, I, O>> output) {
        return strings(
            parser,
            (negate, types) ->
                output.accept(
                    new ActionFilterNode<>() {
                      @Override
                      public <F> Optional<F> generate(
                          Function<String, Optional<F>> existing,
                          ActionFilterBuilder<F, T, S, I, O> builder,
                          ErrorConsumer errorHandler) {
                        final var result = Optional.of(builder.type(types.stream()));
                        return negate ? result.map(builder::negate) : result;
                      }
                    }),
            strings);
      }
    };

    public abstract <T, S, I, O> Parser parse(
        Parser parser,
        Rule<T> actionState,
        Rule<S> string,
        Rule<S> strings,
        Rule<I> instant,
        Rule<O> offset,
        Consumer<ActionFilterNode<T, S, I, O>> output);
  }

  /**
   * A syntax node that can generate an action filter using a builder
   *
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  public interface ActionFilterNode<T, S, I, O> {

    /**
     * Generate the action filter
     *
     * @param existing build an existing action filter by name
     * @param builder the action filter builder
     * @param errorHandler a collect for errors or warnings
     * @return the constructed filter, if possible
     * @param <F> the filter type
     */
    <F> Optional<F> generate(
        Function<String, Optional<F>> existing,
        ActionFilterBuilder<F, T, S, I, O> builder,
        ErrorConsumer errorHandler);
  }

  private interface BinaryConstructor {
    <F> F create(ActionFilterBuilder<F, ?, ?, ?, ?> builder, Stream<F> filters);
  }

  private interface DateTimeNode {

    LocalDateTime generate(LocalDate date);
  }

  private interface InstantNode {
    Instant generate(LocalDateTime datetime);
  }

  private interface TagParser {
    <T, S, I, O> Parser parseTag(
        Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output);
  }

  private interface TemporalBuilder {
    static <T, S, I, O> Parser parse(
        Parser parser,
        TemporalBuilder temporal,
        Rule<I> timeRule,
        Rule<O> offsetRule,
        Consumer<ActionFilterNode<T, S, I, O>> output) {
      final var type = new AtomicReference<TemporalType>();
      final var result = parser.whitespace().dispatch(TEMPORAL, type::set).whitespace();
      if (result.isGood()) {
        return type.get().parse(result, temporal, timeRule, offsetRule, output);
      }
      return result;
    }

    <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset);

    <F, T, S, I, O> F range(
        ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end);
  }

  private interface TextParser {
    <T, S, I, O> Parser parseText(
        Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output);
  }

  private static final Pattern ACTION_ID = Pattern.compile("shesmu:([0-9a-fA-F]{40})");
  private static final Pattern ACTION_ID_HASH = Pattern.compile("[0-9a-fA-F]{40}");
  private static final Parser.ParseDispatch<ActionState> ACTION_STATE =
      new Parser.ParseDispatch<>();
  private static final TemporalBuilder BUILD_ADDED =
      new TemporalBuilder() {

        @Override
        public <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset) {
          return builder.addedAgo(offset);
        }

        @Override
        public <F, T, S, I, O> F range(
            ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end) {
          return builder.added(start, end);
        }
      };
  private static final TemporalBuilder BUILD_CHECKED =
      new TemporalBuilder() {
        @Override
        public <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset) {
          return builder.checkedAgo(offset);
        }

        @Override
        public <F, T, S, I, O> F range(
            ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end) {
          return builder.checked(start, end);
        }
      };
  private static final TemporalBuilder BUILD_CREATED =
      new TemporalBuilder() {
        @Override
        public <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset) {
          return builder.createdAgo(offset);
        }

        @Override
        public <F, T, S, I, O> F range(
            ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end) {
          return builder.created(start, end);
        }
      };
  private static final TemporalBuilder BUILD_EXTERNAL =
      new TemporalBuilder() {
        @Override
        public <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset) {
          return builder.externalAgo(offset);
        }

        @Override
        public <F, T, S, I, O> F range(
            ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end) {
          return builder.external(start, end);
        }
      };
  private static final TemporalBuilder BUILD_STATUS_CHANGED =
      new TemporalBuilder() {
        @Override
        public <F, T, S, I, O> F ago(ActionFilterBuilder<F, T, S, I, O> builder, O offset) {
          return builder.statusChangedAgo(offset);
        }

        @Override
        public <F, T, S, I, O> F range(
            ActionFilterBuilder<F, T, S, I, O> builder, Optional<I> start, Optional<I> end) {
          return builder.statusChanged(start, end);
        }
      };
  private static final Parser.ParseDispatch<Comparison> COMPARISON = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Supplier<LocalDate>> DATE =
      new Parser.ParseDispatch<>();
  private static final Pattern DATE_PATTERN =
      Pattern.compile("(\\d{4})-(\\d{2}|[A-Za-z]+)-(\\d{2})");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  /** A rule to parse an action state */
  public static final RuleWithLiteral<ActionState, ActionState> PARSE_ACTION_STATE =
      new RuleWithLiteral<>() {
        @Override
        public ActionState literal(ActionState value) {
          return value;
        }

        @Override
        public Parser parse(Parser parser, Consumer<ActionState> output) {
          return parser.dispatch(ACTION_STATE, output).whitespace();
        }
      };

  /** A regular expression to match a regular expression during parsing */
  public static final Pattern REGEX = Pattern.compile("^/((?:[^\\\\/\n]|\\\\.)*)/(i)?");

  private static final Pattern SEARCH =
      Pattern.compile(
          "shesmusearch:((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|))");
  private static final Pattern SEARCH_BASE64 =
      Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|)");
  private static final Pattern SOURCE_HASH = Pattern.compile("[0-9a-fA-F]+");
  private static final Parser.ParseDispatch<Pair<Boolean, List<SourceOliveLocation>>> SOURCE_MATCH =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<String> STRING = new Parser.ParseDispatch<>();
  /** A rule to parse a string */
  public static final RuleWithLiteral<String, String> PARSE_STRING =
      new RuleWithLiteral<>() {
        @Override
        public String literal(String value) {
          return value;
        }

        @Override
        public Parser parse(Parser parser, Consumer<String> output) {
          return parser.whitespace().dispatch(STRING, output).whitespace();
        }
      };
  /** A regular expression to match the contents of a string */
  public static final Pattern STRING_CONTENTS = Pattern.compile("^[^\"\n\\\\]*");

  private static final Parser.ParseDispatch<TagParser> TAG_MATCHER = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<TemporalType> TEMPORAL = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Integer> TEMPORAL_UNITS = new Parser.ParseDispatch<>();
  /** A rule to parse a temporal offset */
  public static final RuleWithLiteral<Long, Long> PARSE_OFFSET =
      new RuleWithLiteral<>() {
        @Override
        public Long literal(Long value) {
          return value;
        }

        @Override
        public Parser parse(Parser parser, Consumer<Long> output) {
          final var time = new AtomicLong();
          final var units = new AtomicInteger();
          final var result =
              parser
                  .whitespace()
                  .integer(time::set, 10)
                  .whitespace()
                  .dispatch(TEMPORAL_UNITS, units::set)
                  .whitespace();
          if (result.isGood()) {
            output.accept(time.get() * units.get());
          }
          return result;
        }
      };

  private static final Parser.ParseDispatch<TextParser> TEXT_MATCH = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<DateTimeNode> TIME = new Parser.ParseDispatch<>();
  private static final Pattern TIME_PATTERN =
      Pattern.compile("(?:(?:T| *)(\\d{2}):(\\d{2})(?::(\\d{2})))?");
  private static final Parser.ParseDispatch<InstantNode> TIME_ZONE = new Parser.ParseDispatch<>();
  /** A rule to parse an exact time */
  public static final RuleWithLiteral<Instant, Instant> PARSE_TIME =
      new RuleWithLiteral<>() {
        @Override
        public Instant literal(Instant value) {
          return value;
        }

        @Override
        public Parser parse(Parser parser, Consumer<Instant> output) {
          final var date = new AtomicReference<Supplier<LocalDate>>();
          final var time = new AtomicReference<DateTimeNode>();
          final var zone = new AtomicReference<InstantNode>();
          final var result =
              parser
                  .dispatch(DATE, date::set)
                  .dispatch(TIME, time::set)
                  .dispatch(TIME_ZONE, zone::set)
                  .whitespace();
          if (result.isGood()) {
            output.accept(zone.get().generate(time.get().generate(date.get().get())));
          }
          return result;
        }
      };

  private static final Parser.ParseDispatch<Variable> VARIABLE = new Parser.ParseDispatch<>();

  static {
    for (final var state : ActionState.values()) {
      ACTION_STATE.addKeyword(state.name().toLowerCase(), Parser.just(state));
    }
    for (final var type : TemporalType.values()) {
      TEMPORAL.addKeyword(type.name().toLowerCase(), Parser.just(type));
    }
    for (final var variable : Variable.values()) {
      VARIABLE.addKeyword(variable.name().toLowerCase(), Parser.just(variable));
    }
    for (final var comparison : Comparison.values()) {
      COMPARISON.addSymbol(comparison.symbol, Parser.just(comparison));
      TAG_MATCHER.addSymbol(comparison.symbol, Parser.just(comparison));
    }

    TAG_MATCHER.addSymbol("~", tagRegex(false));
    TAG_MATCHER.addSymbol("!~", tagRegex(true));

    TEXT_MATCH.addSymbol("~", textRegex(false));
    TEXT_MATCH.addSymbol("!~", textRegex(true));
    TEXT_MATCH.addSymbol("=", textExact(false));
    TEXT_MATCH.addSymbol("!=", textExact(true));

    SOURCE_MATCH.addSymbol(
        "=",
        (p, o) ->
            p.whitespace()
                .then(ActionFilter::parseLocation, s -> o.accept(new Pair<>(false, List.of(s))))
                .whitespace());
    SOURCE_MATCH.addSymbol(
        "!=",
        (p, o) ->
            p.whitespace()
                .then(ActionFilter::parseLocation, s -> o.accept(new Pair<>(true, List.of(s))))
                .whitespace());
    SOURCE_MATCH.addSymbol(
        "in",
        (p, o) ->
            p.whitespace()
                .symbol("(")
                .whitespace()
                .list(s -> o.accept(new Pair<>(false, s)), ActionFilter::parseLocation, ',')
                .symbol(")")
                .whitespace());
    SOURCE_MATCH.addSymbol(
        "not",
        (p, o) ->
            p.whitespace()
                .keyword("in")
                .whitespace()
                .symbol("(")
                .whitespace()
                .list(s -> o.accept(new Pair<>(true, s)), ActionFilter::parseLocation, ',')
                .symbol(")")
                .whitespace());
    STRING.addSymbol(
        "\"",
        (p, o) ->
            p.regex(STRING_CONTENTS, m -> o.accept(m.group(0)), "string contents")
                .symbol("\"")
                .whitespace());
    STRING.addRaw("identifier", (p, o) -> p.identifier(o).whitespace());
    TEMPORAL_UNITS.addKeyword("days", Parser.just(86_400_000));
    TEMPORAL_UNITS.addKeyword("day", Parser.just(86_400_000));
    TEMPORAL_UNITS.addKeyword("d", Parser.just(86_400_000));
    TEMPORAL_UNITS.addKeyword("hours", Parser.just(3600_000));
    TEMPORAL_UNITS.addKeyword("hour", Parser.just(3600_000));
    TEMPORAL_UNITS.addKeyword("h", Parser.just(3600_000));
    TEMPORAL_UNITS.addKeyword("mins", Parser.just(60_000));
    TEMPORAL_UNITS.addKeyword("min", Parser.just(60_000));
    TEMPORAL_UNITS.addKeyword("m", Parser.just(60_000));
    TEMPORAL_UNITS.addKeyword("secs", Parser.just(1000));
    TEMPORAL_UNITS.addKeyword("sec", Parser.just(1000));
    TEMPORAL_UNITS.addKeyword("s", Parser.just(1000));
    TEMPORAL_UNITS.addKeyword("millis", Parser.just(1));
    TEMPORAL_UNITS.addRaw("nothing", Parser.just(1000));
    DATE.addKeyword("today", Parser.just(LocalDate::now));
    DATE.addKeyword("yesterday", Parser.just(() -> LocalDate.now().minusDays(1)));
    for (final var weekday : DayOfWeek.values()) {
      DATE.addKeyword(
          weekday.name().toLowerCase(), Parser.just(() -> LocalDate.now().with(weekday)));
    }
    DATE.addRaw(
        "ISO-8601 date",
        (p, o) -> {
          final var matcher = new AtomicReference<Matcher>();
          final var result = p.regex(DATE_PATTERN, matcher::set, "ISO-8601 date").whitespace();
          if (result.isGood()) {
            {
              final var year = Integer.parseInt(matcher.get().group(1));
              final int month;
              final var day = Integer.parseInt(matcher.get().group(3));
              if (Character.isDigit(matcher.get().group(2).charAt(0))) {
                month = Integer.parseInt(matcher.get().group(2));
              } else {
                final var monthValue = parseMonth(matcher.get().group(2).toLowerCase());
                if (monthValue.isPresent()) {
                  month = monthValue.getAsInt();
                } else {
                  return p.raise(String.format("Unknown month “%s”.", matcher.get().group(2)));
                }
              }
              o.accept(() -> LocalDate.of(year, month, day));
            }
          }
          return result;
        });
    TIME.addSymbol("current", Parser.justWhiteSpace((date) -> date.atTime(LocalTime.now())));
    TIME.addSymbol("midnight", Parser.justWhiteSpace((date) -> date.atTime(LocalTime.MIDNIGHT)));
    TIME.addSymbol("noon", Parser.justWhiteSpace((date) -> date.atTime(LocalTime.NOON)));
    TIME.addRaw(
        "ISO-8601 time",
        (p, o) -> {
          final var matcher = new AtomicReference<Matcher>();
          final var result = p.regex(TIME_PATTERN, matcher::set, "ISO-8601 time").whitespace();

          if (result.isGood()) {
            final var hour = Integer.parseInt(matcher.get().group(1));
            final var minute =
                matcher.get().group(2).isEmpty() ? 0 : Integer.parseInt(matcher.get().group(2));
            final var second =
                matcher.get().group(3).isEmpty() ? 0 : Integer.parseInt(matcher.get().group(3));
            if (!ChronoField.HOUR_OF_DAY.range().isValidValue(hour)) {
              return p.raise("Invalid hour");
            }
            if (!ChronoField.MINUTE_OF_HOUR.range().isValidValue(minute)) {
              return p.raise("Invalid minute");
            }
            if (!ChronoField.SECOND_OF_MINUTE.range().isValidValue(second)) {
              return p.raise("Invalid second");
            }
            o.accept(n -> n.atTime(LocalTime.of(hour, minute, second)));
          }
          return result;
        });
    TIME_ZONE.addKeyword(
        "utc", Parser.justWhiteSpace((datetime) -> datetime.atZone(ZoneId.of("Z")).toInstant()));
    TIME_ZONE.addKeyword(
        "server",
        Parser.justWhiteSpace((datetime) -> datetime.atZone(ZoneId.systemDefault()).toInstant()));
    TIME_ZONE.addRaw(
        "nothing",
        Parser.justWhiteSpace((datetime) -> datetime.atZone(ZoneId.systemDefault()).toInstant()));
  }

  private static <T, S, I, O> Parser.Rule<BinaryOperator<ActionFilterNode<T, S, I, O>>> binary(
      String keyword, BinaryConstructor constructor) {
    return (p, o) -> {
      final var result = p.keyword(keyword).whitespace();
      if (result.isGood()) {
        o.accept(
            (a, b) ->
                new ActionFilterNode<>() {
                  @Override
                  public <F> Optional<F> generate(
                      Function<String, Optional<F>> existing,
                      ActionFilterBuilder<F, T, S, I, O> builder,
                      ErrorConsumer errorHandler) {
                    final var aValue = a.generate(existing, builder, errorHandler);
                    final var bValue = b.generate(existing, builder, errorHandler);
                    if (aValue.isPresent() && bValue.isPresent()) {
                      return Optional.of(
                          constructor.create(builder, Stream.of(aValue.get(), bValue.get())));
                    }
                    return Optional.empty();
                  }
                });
      }
      return result;
    };
  }

  /**
   * Find action filters and action identifiers encoded as exported URL-like objects in arbitrary
   * text
   *
   * @param text the text to search
   * @param mapper an object mapper for decoding JSON data
   * @return a filter that contains the union of any filters or action identifiers discovered, if
   *     any were
   */
  public static Optional<ActionFilter> extractFromText(String text, ObjectMapper mapper) {
    final Set<String> actionIds = new TreeSet<>();
    final List<ActionFilter> filters = new ArrayList<>();
    final var actionMatcher = ACTION_ID.matcher(text);
    while (actionMatcher.find()) {
      actionIds.add("shesmu:" + actionMatcher.group(1).toUpperCase());
    }
    if (!actionIds.isEmpty()) {
      final var idFilter = new ActionFilterIds();
      idFilter.setIds(new ArrayList<>(actionIds));
      filters.add(idFilter);
    }
    final var filterMatcher = SEARCH.matcher(text);
    while (filterMatcher.find()) {
      try {
        final var current =
            mapper.readValue(
                Base64.getDecoder().decode(filterMatcher.group(1)), ActionFilter[].class);
        switch (current.length) {
          case 0:
            break;
          case 1:
            filters.add(current[0]);
            break;
          default:
            final var and = new ActionFilterAnd();
            and.setFilters(current);
            filters.add(and);
        }
      } catch (Exception e) {
        // That was some hot garbage we're going to ignore
      }
    }
    switch (filters.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(filters.get(0));
      default:
        final var or = new ActionFilterOr();
        or.setFilters(filters.toArray(ActionFilter[]::new));
        return Optional.of(or);
    }
  }

  /**
   * Take the base filter and intersect it with the union of all accessory filters
   *
   * @param baseName the name for the joined result
   * @param baseFilters the filters to always include in every result
   * @param accessoryFilters the set of filters to join against
   * @param builder the filter builder
   * @return a stream of labelled constructed filters
   * @param <F> the resulting filter type
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  public static <F, T, S, I, O> Stream<Pair<String, F>> joinAllAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F, T, S, I, O> builder) {
    return Stream.of(
        new Pair<>(
            baseName,
            builder.and(Stream.of(baseFilters, builder.or(accessoryFilters.map(Pair::second))))));
  }

  /**
   * Take the base filter and remove all the accessory filters
   *
   * @param baseName the name for the joined result
   * @param baseFilters the filters to always include in every result
   * @param accessoryFilters the set of filters to remove
   * @param builder the filter builder
   * @return a stream of labelled constructed filters
   * @param <F> the resulting filter type
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  public static <F, T, S, I, O> Stream<Pair<String, F>> joinAllExcept(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F, T, S, I, O> builder) {
    return Stream.of(
        new Pair<>(
            baseName,
            builder.and(
                Stream.concat(
                    Stream.of(baseFilters),
                    accessoryFilters.map(Pair::second).map(builder::negate)))));
  }

  /**
   * Take each accessory filter and produce the intersection of the base filter and the accessory
   * filter
   *
   * @param baseName the name for the joined result
   * @param baseFilters the filters to always include in every result
   * @param accessoryFilters the set of filters to join against
   * @param builder the filter builder
   * @return a stream of labelled constructed filters
   * @param <F> the resulting filter type
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  public static <F, T, S, I, O> Stream<Pair<String, F>> joinEachAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F, T, S, I, O> builder) {
    return accessoryFilters.map(
        p -> new Pair<>(p.first(), builder.and(Stream.of(p.second(), baseFilters))));
  }

  /**
   * Parse an action filter using the parse rules provided
   *
   * @param parser the parser state
   * @param actionState the rule to parse action states
   * @param string the rule to parse strings
   * @param strings the rule to parse sets of strings
   * @param instant the rule to parse time instants
   * @param offset the rule to parse time offsets
   * @param output the callback to hold the output
   * @return the output parser state
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  public static <T, S, I, O> Parser parse(
      Parser parser,
      Rule<T> actionState,
      RuleWithLiteral<S, String> string,
      RuleWithLiteral<S, String> strings,
      RuleWithLiteral<I, Instant> instant,
      RuleWithLiteral<O, Long> offset,
      Consumer<ActionFilterNode<T, S, I, O>> output) {
    return Parser.scanBinary(
        (p, o) -> parse1(p, actionState, string, strings, instant, offset, o),
        binary(
            "or",
            new BinaryConstructor() {
              @Override
              public <F> F create(ActionFilterBuilder<F, ?, ?, ?, ?> builder, Stream<F> filters) {
                return builder.or(filters);
              }
            }),
        parser,
        output);
  }

  private static <T, S, I, O> Parser parse1(
      Parser parser,
      Rule<T> actionState,
      RuleWithLiteral<S, String> string,
      RuleWithLiteral<S, String> strings,
      RuleWithLiteral<I, Instant> instant,
      RuleWithLiteral<O, Long> offset,
      Consumer<ActionFilterNode<T, S, I, O>> output) {
    return Parser.scanBinary(
        (p, o) -> parse2(p, actionState, string, strings, instant, offset, o),
        binary(
            "and",
            new BinaryConstructor() {
              @Override
              public <F> F create(ActionFilterBuilder<F, ?, ?, ?, ?> builder, Stream<F> filters) {
                return builder.and(filters);
              }
            }),
        parser,
        output);
  }

  private static <T, S, I, O> Parser parse2(
      Parser parser,
      Rule<T> actionState,
      RuleWithLiteral<S, String> string,
      RuleWithLiteral<S, String> strings,
      RuleWithLiteral<I, Instant> instant,
      RuleWithLiteral<O, Long> offset,
      Consumer<ActionFilterNode<T, S, I, O>> output) {
    return Parser.scanPrefixed(
        (p, o) -> parse3(p, actionState, string, strings, instant, offset, o),
        (p, o) -> {
          final var result = p.keyword("not").whitespace();
          if (result.isGood()) {
            o.accept(
                node ->
                    new ActionFilterNode<>() {
                      @Override
                      public <F> Optional<F> generate(
                          Function<String, Optional<F>> existing,
                          ActionFilterBuilder<F, T, S, I, O> builder,
                          ErrorConsumer errorHandler) {
                        return node.generate(existing, builder, errorHandler).map(builder::negate);
                      }
                    });
          }
          return result;
        },
        parser,
        output);
  }

  private static <T, S, I, O> Parser parse3(
      Parser parser,
      Rule<T> actionState,
      RuleWithLiteral<S, String> string,
      RuleWithLiteral<S, String> strings,
      RuleWithLiteral<I, Instant> instant,
      RuleWithLiteral<O, Long> offset,
      Consumer<ActionFilterNode<T, S, I, O>> output) {
    final var actionResult =
        parser
            .symbol("shesmu:")
            .regex(
                ACTION_ID_HASH,
                m ->
                    output.accept(
                        new ActionFilterNode<>() {
                          private final List<S> ids =
                              List.of(string.literal("shesmu:" + m.group(0).toUpperCase()));

                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            return Optional.of(builder.ids(ids));
                          }
                        }),
                "hexadecimal ID");
    if (actionResult.isGood()) {
      return actionResult;
    }
    final var searchParser =
        parser
            .whitespace()
            .symbol("shesmusearch:")
            .regex(
                SEARCH_BASE64,
                m ->
                    output.accept(
                        new ActionFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {
                            final var extracted = extractFromText(m.group(0), MAPPER);
                            if (extracted.isEmpty()) {
                              errorHandler.raise(
                                  parser.line(), parser.column(), "Invalid search string.");
                            }
                            return extracted.map(builder::fromJson);
                          }
                        }),
                "Base64 search string");
    if (searchParser.isGood()) {
      return searchParser;
    }

    final var knownParser =
        parser
            .symbol("known:")
            .whitespace()
            .dispatch(
                STRING,
                name ->
                    output.accept(
                        new ActionFilterNode<>() {

                          @Override
                          public <F> Optional<F> generate(
                              Function<String, Optional<F>> existing,
                              ActionFilterBuilder<F, T, S, I, O> builder,
                              ErrorConsumer errorHandler) {

                            final var filter = existing.apply(name);
                            if (filter.isPresent()) {
                              return filter;
                            } else {
                              errorHandler.raise(
                                  parser.line(),
                                  parser.column(),
                                  String.format("Unknown exist search function %s", name));
                              return Optional.empty();
                            }
                          }
                        }))
            .whitespace();
    if (knownParser.isGood()) {
      return knownParser;
    }
    final var subExpressionParser = parser.symbol("(");
    if (subExpressionParser.isGood()) {
      return parse(
              subExpressionParser.whitespace(),
              actionState,
              string,
              strings,
              instant,
              offset,
              output)
          .symbol(")")
          .whitespace();
    }
    final var variable = new AtomicReference<Variable>();
    final var result = parser.dispatch(VARIABLE, variable::set).whitespace();
    if (result.isGood()) {
      return variable
          .get()
          .parse(result, actionState, string, strings, instant, offset, output)
          .whitespace();
    }
    return result;
  }

  private static Parser parseLocation(Parser parser, Consumer<SourceOliveLocation> output) {
    final var location = new SourceOliveLocation();
    final var filename = new AtomicReference<List<String>>();
    var result =
        parser
            .whitespace()
            .symbol("\"")
            .regex(STRING_CONTENTS, m -> location.setFile(m.group(0)), "filename")
            .symbol("\"")
            .whitespace();
    if (result.isGood()) {
      final var line =
          result.symbol(":").whitespace().integer(v -> location.setLine((int) v), 10).whitespace();
      if (line.isGood()) {
        result = line;
        final var column =
            result
                .symbol(":")
                .whitespace()
                .integer(v -> location.setColumn((int) v), 10)
                .whitespace();
        if (column.isGood()) {
          result = column;
          final var hash =
              result
                  .symbol("[")
                  .whitespace()
                  .regex(
                      SOURCE_HASH, m -> location.setHash(m.group(0).toUpperCase()), "Source hash")
                  .whitespace()
                  .symbol("]")
                  .whitespace();
          if (hash.isGood()) {
            result = hash;
          }
        }
      }
    }
    if (result.isGood()) {
      output.accept(location);
    }
    return result;
  }

  private static OptionalInt parseMonth(String inputMonth) {
    final var symbols = new DateFormatSymbols();
    final var monthNames = symbols.getMonths();
    final var monthShortNames = symbols.getShortMonths();
    for (var i = 0; i < monthNames.length; i++) {
      if (inputMonth.equalsIgnoreCase(monthNames[i])
          || inputMonth.equalsIgnoreCase(monthShortNames[i])) {
        return OptionalInt.of(i + 1);
      }
    }
    return OptionalInt.empty();
  }

  /**
   * Parse an action filter query
   *
   * @param input the query to parse
   * @param existing any pre-defined action filters for named variable substitution
   * @param errorHandler a collector for errors
   * @return the resulting action filter, if successful
   */
  public static Optional<ActionFilter> parseQuery(
      String input, Function<String, Optional<ActionFilter>> existing, ErrorConsumer errorHandler) {
    final var query = new AtomicReference<ActionFilterNode<ActionState, String, Instant, Long>>();
    final var parser =
        parse(
            Parser.start(input, errorHandler).whitespace(),
            PARSE_ACTION_STATE,
            PARSE_STRING,
            PARSE_STRING,
            PARSE_TIME,
            PARSE_OFFSET,
            query::set);
    return parser.finished()
        ? Optional.of(query.get())
            .flatMap(q -> q.generate(existing, ActionFilterBuilder.JSON, errorHandler))
        : Optional.empty();
  }

  private static <T> Parser strings(
      Parser parser, BiConsumer<Boolean, List<T>> output, Rule<T> valueParser) {
    final var comparison = new AtomicReference<Comparison>();
    final var result = parser.whitespace().dispatch(COMPARISON, comparison::set).whitespace();
    if (result.isGood()) {
      return comparison.get().parse(result, valueParser, output).whitespace();
    }
    return result;
  }

  private static Rule<TagParser> tagRegex(boolean negate) {
    return Parser.just(
        new TagParser() {
          @Override
          public <T, S, I, O> Parser parseTag(
              Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output) {
            return parser
                .whitespace()
                .regex(
                    REGEX,
                    m ->
                        output.accept(
                            new ActionFilterNode<>() {
                              @Override
                              public <F> Optional<F> generate(
                                  Function<String, Optional<F>> existing,
                                  ActionFilterBuilder<F, T, S, I, O> builder,
                                  ErrorConsumer errorHandler) {
                                try {
                                  final var result =
                                      builder.tag(
                                          Pattern.compile(
                                              m.group(1),
                                              (m.group(2) == null || m.group(2).length() == 0)
                                                  ? Pattern.CASE_INSENSITIVE
                                                  : 0));
                                  return Optional.of(negate ? builder.negate(result) : result);
                                } catch (Exception e) {
                                  errorHandler.raise(
                                      parser.line(),
                                      parser.column(),
                                      e.getMessage().split("\n")[0]);
                                  return Optional.empty();
                                }
                              }
                            }),
                    "regular expression");
          }
        });
  }

  private static Rule<TextParser> textExact(boolean negate) {
    return Parser.just(
        new TextParser() {
          @Override
          public <T, S, I, O> Parser parseText(
              Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output) {
            return parser
                .whitespace()
                .then(
                    rule,
                    s ->
                        output.accept(
                            new ActionFilterNode<>() {
                              @Override
                              public <F> Optional<F> generate(
                                  Function<String, Optional<F>> existing,
                                  ActionFilterBuilder<F, T, S, I, O> builder,
                                  ErrorConsumer errorHandler) {
                                final var result = builder.textSearch(s, true);
                                return Optional.of(negate ? builder.negate(result) : result);
                              }
                            }));
          }
        });
  }

  private static Rule<TextParser> textRegex(boolean negate) {
    return Parser.just(
        new TextParser() {
          @Override
          public <T, S, I, O> Parser parseText(
              Parser parser, Rule<S> rule, Consumer<ActionFilterNode<T, S, I, O>> output) {
            return parser
                .whitespace()
                .regex(
                    REGEX,
                    m ->
                        output.accept(
                            new ActionFilterNode<>() {
                              @Override
                              public <F> Optional<F> generate(
                                  Function<String, Optional<F>> existing,
                                  ActionFilterBuilder<F, T, S, I, O> builder,
                                  ErrorConsumer errorHandler) {
                                try {
                                  final var result =
                                      builder.textSearch(
                                          Pattern.compile(
                                              m.group(1),
                                              (m.group(2) == null || m.group(2).length() == 0)
                                                  ? Pattern.CASE_INSENSITIVE
                                                  : 0));
                                  return Optional.of(negate ? builder.negate(result) : result);
                                } catch (Exception e) {
                                  errorHandler.raise(
                                      parser.line(),
                                      parser.column(),
                                      e.getMessage().split("\n")[0]);
                                  return Optional.empty();
                                }
                              }
                            }),
                    "regular expression");
          }
        });
  }

  private boolean negate;

  /**
   * Convert a filter to a real implementation using the standard type parameters
   *
   * @param builder the filter builder
   * @return the constructed filter
   * @param <F> the filter type
   */
  public abstract <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> builder);

  /**
   * Checks whether the filter's output is inverted
   *
   * @return if true, invert the filter's output
   */
  public boolean isNegate() {
    return negate;
  }

  /**
   * A utility function to negate a filter if set
   *
   * @param filter the filter to be negated
   * @param filterBuilder the filter builder
   * @return the possibly negated filter
   * @param <F> the resulting filter type
   * @param <T> action state type
   * @param <S> string type
   * @param <I> time instant type
   * @param <O> time offset type
   */
  protected <F, T, S, I, O> F maybeNegate(
      F filter, ActionFilterBuilder<F, T, S, I, O> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  /**
   * Sets whether the sense of the filter should be inverted
   *
   * @param negate if true, invert the filter's output
   */
  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
