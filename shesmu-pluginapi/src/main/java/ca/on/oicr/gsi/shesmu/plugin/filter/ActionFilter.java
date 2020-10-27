package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.DateFormatSymbols;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ActionFilterAdded.class, name = "added"),
  @JsonSubTypes.Type(value = ActionFilterAddedAgo.class, name = "addedago"),
  @JsonSubTypes.Type(value = ActionFilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = ActionFilterChecked.class, name = "checked"),
  @JsonSubTypes.Type(value = ActionFilterCheckedAgo.class, name = "checkedago"),
  @JsonSubTypes.Type(value = ActionFilterExternal.class, name = "external"),
  @JsonSubTypes.Type(value = ActionFilterExternalAgo.class, name = "externalago"),
  @JsonSubTypes.Type(value = ActionFilterIds.class, name = "id"),
  @JsonSubTypes.Type(value = ActionFilterOr.class, name = "or"),
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
  private enum Variable implements Parser.Rule<ActionFilterNode> {
    GENERATED {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return temporal(parser, output, ActionFilterAddedAgo::new, ActionFilterAdded::new);
      }
    },
    CHECKED {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return temporal(parser, output, ActionFilterCheckedAgo::new, ActionFilterChecked::new);
      }
    },
    EXTERNAL {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return temporal(parser, output, ActionFilterExternalAgo::new, ActionFilterExternal::new);
      }
    },
    FILE {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return strings(
            parser,
            output,
            files -> {
              final ActionFilterSourceFile filter = new ActionFilterSourceFile();
              filter.setFiles(files);
              return filter;
            },
            Function.identity(),
            String[]::new);
      }
    },
    SOURCE {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        final AtomicReference<Pair<Boolean, List<SourceOliveLocation>>> matches =
            new AtomicReference<>();
        final Parser result = parser.whitespace().dispatch(SOURCE_MATCH, matches::set).whitespace();
        if (result.isGood()) {
          output.accept(
              errorHandler -> {
                final ActionFilterSourceLocation filter = new ActionFilterSourceLocation();
                filter.setLocations(
                    matches.get().second().stream().toArray(SourceOliveLocation[]::new));
                filter.setNegate(matches.get().first());
                return Optional.of(filter);
              });
        }
        return result;
      }
    },
    STATUS {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return strings(
            parser,
            output,
            states -> {
              final ActionFilterStatus filter = new ActionFilterStatus();
              filter.setState(states);
              return filter;
            },
            s -> ActionState.valueOf(s.toUpperCase()),
            ActionState[]::new);
      }
    },

    STATUS_CHANGED {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return temporal(
            parser, output, ActionFilterStatusChangedAgo::new, ActionFilterStatusChanged::new);
      }
    },
    TAG {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return parser.dispatch(TAG_MATCH, output);
      }
    },
    TEXT {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return parser.dispatch(TEXT_MATCH, output);
      }
    },
    TYPE {
      @Override
      public Parser parse(Parser parser, Consumer<ActionFilterNode> output) {
        return strings(
            parser,
            output,
            types -> {
              final ActionFilterType filter = new ActionFilterType();
              filter.setTypes(types);
              return filter;
            },
            Function.identity(),
            String[]::new);
      }
    };
  }

  private interface ActionFilterNode {
    Optional<ActionFilter> generate(ErrorConsumer errorHandler);
  }

  interface DateNode {
    Optional<LocalDate> generate(ErrorConsumer errorHandler);
  }

  interface DateTimeNode {
    Optional<LocalDateTime> generate(LocalDate date, ErrorConsumer errorHandler);
  }

  interface InstantNode {
    Optional<Long> generate(LocalDateTime datetime, ErrorConsumer errorHandler);
  }

  private interface TimeNode {
    Optional<ActionFilter> generate(
        Supplier<? extends BaseAgoActionFilter> agoConstructor,
        Supplier<? extends BaseRangeActionFilter> rangeConstructor,
        ErrorConsumer errorHandler);
  }

  private static Parser.Rule<BinaryOperator<ActionFilterNode>> binary(
      Supplier<? extends BaseCollectionActionFilter> constructor) {
    return Parser.just(
        (a, b) ->
            errorHandler -> {
              final Optional<ActionFilter> aValue = a.generate(errorHandler);
              final Optional<ActionFilter> bValue = b.generate(errorHandler);
              if (aValue.isPresent() && bValue.isPresent()) {
                final BaseCollectionActionFilter result = constructor.get();
                result.setFilters(new ActionFilter[] {aValue.get(), bValue.get()});
                return Optional.of(result);
              }
              return Optional.empty();
            });
  }

  public static Optional<ActionFilter> extractFromText(String text, ObjectMapper mapper) {
    final Set<String> actionIds = new TreeSet<>();
    final List<ActionFilter> filters = new ArrayList<>();
    final Matcher actionMatcher = ACTION_ID.matcher(text);
    while (actionMatcher.find()) {
      actionIds.add("shesmu:" + actionMatcher.group(1).toUpperCase());
    }
    if (!actionIds.isEmpty()) {
      final ActionFilterIds idFilter = new ActionFilterIds();
      idFilter.setIds(new ArrayList<>(actionIds));
      filters.add(idFilter);
    }
    final Matcher filterMatcher = SEARCH.matcher(text);
    while (filterMatcher.find()) {
      try {
        final ActionFilter[] current =
            mapper.readValue(
                Base64.getDecoder().decode(filterMatcher.group(1)), ActionFilter[].class);
        switch (current.length) {
          case 0:
            break;
          case 1:
            filters.add(current[0]);
            break;
          default:
            final ActionFilterAnd and = new ActionFilterAnd();
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
        final ActionFilterOr or = new ActionFilterOr();
        or.setFilters(filters.stream().toArray(ActionFilter[]::new));
        return Optional.of(or);
    }
  }

  /** Take the base filter and intersect it with the union of all accessory filters */
  public static <F> Stream<Pair<String, F>> joinAllAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
    return Stream.of(
        new Pair<>(
            baseName,
            builder.and(Stream.of(baseFilters, builder.or(accessoryFilters.map(Pair::second))))));
  }

  /** Take the base filter and remove all the accessory filters */
  public static <F> Stream<Pair<String, F>> joinAllExcept(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
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
   */
  public static <F> Stream<Pair<String, F>> joinEachAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
    return accessoryFilters.map(
        p -> new Pair<>(p.first(), builder.and(Stream.of(p.second(), baseFilters))));
  }

  private static Parser parse0(Parser parser, Consumer<ActionFilterNode> output) {
    return Parser.scanBinary(ActionFilter::parse1, OR, parser, output);
  }

  private static Parser parse1(Parser parser, Consumer<ActionFilterNode> output) {
    return Parser.scanBinary(ActionFilter::parse2, AND, parser, output);
  }

  private static Parser parse2(Parser parser, Consumer<ActionFilterNode> output) {
    return Parser.scanPrefixed(ActionFilter::parse3, NOT, parser, output);
  }

  private static Parser parse3(Parser parser, Consumer<ActionFilterNode> output) {
    return parser.dispatch(TERMINAL, output).whitespace();
  }

  private static Parser parseAgo(Parser parser, Consumer<TimeNode> output, boolean negate) {
    final AtomicLong time = new AtomicLong();
    final AtomicInteger units = new AtomicInteger();
    final Parser result =
        parser
            .whitespace()
            .integer(time::set, 10)
            .dispatch(TEMPORAL_UNITS, units::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          (af, rf, e) -> {
            final BaseAgoActionFilter filter = af.get();
            filter.setOffset(time.get() * units.get());
            filter.setNegate(negate);
            return Optional.of(filter);
          });
    }
    return result;
  }

  private static Parser parseLocation(Parser parser, Consumer<SourceOliveLocation> output) {
    final SourceOliveLocation location = new SourceOliveLocation();
    final AtomicReference<List<String>> filename = new AtomicReference<>();
    Parser result =
        parser
            .whitespace()
            .symbol("\"")
            .regex(STRING_CONTENTS, m -> location.setFile(m.group(0)), "filename")
            .symbol("\"")
            .whitespace();
    if (result.isGood()) {
      final Parser line =
          result.symbol(":").whitespace().integer(v -> location.setLine((int) v), 10).whitespace();
      if (line.isGood()) {
        result = line;
        final Parser column =
            result
                .symbol(":")
                .whitespace()
                .integer(v -> location.setColumn((int) v), 10)
                .whitespace();
        if (column.isGood()) {
          result = column;
          final Parser hash =
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
    final DateFormatSymbols symbols = new DateFormatSymbols();
    final String[] monthNames = symbols.getMonths();
    final String[] monthShortNames = symbols.getShortMonths();
    for (int i = 0; i < monthNames.length; i++) {
      if (inputMonth.equalsIgnoreCase(monthNames[i])
          || inputMonth.equalsIgnoreCase(monthShortNames[i])) {
        return OptionalInt.of(i + 1);
      }
    }
    return OptionalInt.empty();
  }

  private static Parser parseOpenRange(
      Parser parser, Consumer<TimeNode> output, BiConsumer<BaseRangeActionFilter, Long> setter) {
    final AtomicReference<Function<ErrorConsumer, Optional<Long>>> time = new AtomicReference<>();
    final Parser result = parser.whitespace().then(ActionFilter::parseTime, time::set).whitespace();
    if (result.isGood()) {
      output.accept(
          (af, rf, e) ->
              time.get()
                  .apply(e)
                  .map(
                      i -> {
                        final BaseRangeActionFilter filter = rf.get();
                        setter.accept(filter, i);
                        return filter;
                      }));
    }
    return result;
  }

  public static Optional<ActionFilter> parseQuery(String input, ErrorConsumer errorHandler) {
    final AtomicReference<ActionFilterNode> query = new AtomicReference<>();
    final Parser parser =
        Parser.start(input, errorHandler).whitespace().then(ActionFilter::parse0, query::set);
    return parser.finished()
        ? Optional.of(query.get()).flatMap(q -> q.generate(errorHandler))
        : Optional.empty();
  }

  private static Parser parseRange(Parser parser, Consumer<TimeNode> output, boolean negate) {
    final AtomicReference<Function<ErrorConsumer, Optional<Long>>> startTime =
        new AtomicReference<>();
    final AtomicReference<Function<ErrorConsumer, Optional<Long>>> endTime =
        new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .then(ActionFilter::parseTime, startTime::set)
            .whitespace()
            .keyword("to")
            .whitespace()
            .then(ActionFilter::parseTime, endTime::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          (af, rf, e) ->
              startTime
                  .get()
                  .apply(e)
                  .flatMap(
                      start ->
                          endTime
                              .get()
                              .apply(e)
                              .map(
                                  end -> {
                                    final BaseRangeActionFilter filter = rf.get();
                                    filter.setStart(start);
                                    filter.setEnd(end);
                                    filter.setNegate(negate);
                                    return filter;
                                  })));
    }
    return result;
  }

  private static Parser parseTime(
      Parser parser, Consumer<Function<ErrorConsumer, Optional<Long>>> output) {
    final AtomicReference<DateNode> date = new AtomicReference<>();
    final AtomicReference<DateTimeNode> time = new AtomicReference<>();
    final AtomicReference<InstantNode> zone = new AtomicReference<>();
    final Parser result =
        parser
            .dispatch(DATE, date::set)
            .dispatch(TIME, time::set)
            .dispatch(TIME_ZONE, zone::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          eh ->
              date.get()
                  .generate(eh)
                  .flatMap(d -> time.get().generate(d, eh))
                  .flatMap(ld -> zone.get().generate(ld, eh)));
    }
    return result;
  }

  private static <T> Parser strings(
      Parser parser,
      Consumer<ActionFilterNode> output,
      Function<T[], ? extends ActionFilter> constructor,
      Function<String, T> valueParser,
      IntFunction<T[]> arrayConstructor) {
    final AtomicReference<Pair<Boolean, List<String>>> matches = new AtomicReference<>();
    final Parser result = parser.whitespace().dispatch(SET_MATCH, matches::set).whitespace();
    if (result.isGood()) {
      output.accept(
          errorHandler -> {
            final T[] buffer = arrayConstructor.apply(matches.get().second().size());
            boolean ok = true;
            for (int i = 0; i < buffer.length; i++) {
              try {
                buffer[i] = valueParser.apply(matches.get().second().get(i));
                if (buffer[i] == null) {
                  ok = false;
                }
              } catch (IllegalArgumentException e) {
                ok = false;
                errorHandler.raise(
                    parser.line(),
                    parser.column(),
                    String.format("Unrecognised value “%s”.", matches.get().second().get(i)));
              }
            }
            if (ok) {
              final ActionFilter filter = constructor.apply(buffer);
              filter.setNegate(matches.get().first());
              return Optional.of(filter);
            } else {
              return Optional.empty();
            }
          });
    }
    return result;
  }

  private static Parser temporal(
      Parser parser,
      Consumer<ActionFilterNode> output,
      Supplier<? extends BaseAgoActionFilter> agoConstructor,
      Supplier<? extends BaseRangeActionFilter> rangeConstructor) {
    final AtomicReference<TimeNode> filter = new AtomicReference<>();
    final Parser result = parser.whitespace().dispatch(TEMPORAL, filter::set).whitespace();
    if (result.isGood()) {
      output.accept(
          errorHandler -> filter.get().generate(agoConstructor, rangeConstructor, errorHandler));
    }
    return result;
  }

  private static final Pattern ACTION_ID = Pattern.compile("shesmu:([0-9a-fA-F]{40})");
  private static final Pattern ACTION_ID_HASH = Pattern.compile("[0-9a-fA-F]{40}");
  private static final Parser.ParseDispatch<BinaryOperator<ActionFilterNode>> AND =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<DateNode> DATE = new Parser.ParseDispatch<>();
  private static final Pattern DATE_PATTERN =
      Pattern.compile("(\\d{4})-(\\d{2}|[A-Za-z]+)-(\\d{2})");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Parser.ParseDispatch<UnaryOperator<ActionFilterNode>> NOT =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<BinaryOperator<ActionFilterNode>> OR =
      new Parser.ParseDispatch<>();
  public static final Pattern REGEX = Pattern.compile("^/((?:[^\\\\/\n]|\\\\.)*)/(i)?");
  private static final Pattern SEARCH =
      Pattern.compile(
          "shesmusearch:((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|))");
  private static final Pattern SEARCH_BASE64 =
      Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|)");
  private static final Parser.ParseDispatch<Pair<Boolean, List<String>>> SET_MATCH =
      new Parser.ParseDispatch<>();
  private static final Pattern SOURCE_HASH = Pattern.compile("[0-9a-fA-F]+");
  private static final Parser.ParseDispatch<Pair<Boolean, List<SourceOliveLocation>>> SOURCE_MATCH =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<String> STRING = new Parser.ParseDispatch<>();
  public static final Pattern STRING_CONTENTS = Pattern.compile("^[^\"\n\\\\]*");
  private static final Parser.ParseDispatch<ActionFilterNode> TAG_MATCH =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<TimeNode> TEMPORAL = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Integer> TEMPORAL_UNITS = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<ActionFilterNode> TERMINAL =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<ActionFilterNode> TEXT_MATCH =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<DateTimeNode> TIME = new Parser.ParseDispatch<>();
  private static final Pattern TIME_PATTERN =
      Pattern.compile("(?:(?:T| *)(\\d{2}):(\\d{2})(?::(\\d{2})))?");
  private static final Parser.ParseDispatch<InstantNode> TIME_ZONE = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Variable> VARIABLE = new Parser.ParseDispatch<>();

  static {
    for (final Variable variable : Variable.values()) {
      VARIABLE.addKeyword(variable.name().toLowerCase(), Parser.just(variable));
    }
    OR.addKeyword("or", binary(ActionFilterOr::new));
    AND.addKeyword("and", binary(ActionFilterAnd::new));
    NOT.addKeyword(
        "not",
        Parser.just(
            f ->
                errorHandler -> {
                  final Optional<ActionFilter> result = f.generate(errorHandler);
                  result.ifPresent(v -> v.setNegate(!v.negate));
                  return result;
                }));
    TERMINAL.addSymbol(
        "shesmu:",
        (p, o) ->
            p.regex(
                ACTION_ID_HASH,
                m -> {
                  o.accept(
                      errorHandler -> {
                        final ActionFilterIds filter = new ActionFilterIds();
                        filter.setIds(
                            Collections.singletonList("shesmu:" + m.group(0).toUpperCase()));
                        return Optional.of(filter);
                      });
                },
                "hexadecimal ID"));
    TERMINAL.addSymbol(
        "shesmusearch:",
        (p, o) -> {
          final Parser result =
              p.regex(
                  SEARCH_BASE64,
                  m ->
                      o.accept(
                          errorHandler -> {
                            final Optional<ActionFilter> extracted =
                                extractFromText(m.group(0), MAPPER);
                            if (!extracted.isPresent()) {
                              errorHandler.raise(p.line(), p.column(), "Invalid search string.");
                            }
                            return extracted;
                          }),
                  "Base64 search string");
          return result;
        });
    TERMINAL.addSymbol(
        "(", (p, o) -> p.whitespace().then(ActionFilter::parse0, o).symbol(")").whitespace());
    TERMINAL.addRaw(
        "comparison",
        (p, o) -> {
          final AtomicReference<Variable> variable = new AtomicReference<>();
          final Parser result = p.dispatch(VARIABLE, variable::set).whitespace();
          if (result.isGood()) {
            return result.then(variable.get(), o).whitespace();
          }
          return result;
        });
    TAG_MATCH.addSymbol(
        "~",
        (p, o) ->
            p.whitespace()
                .regex(
                    REGEX,
                    m ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTagRegex filter = new ActionFilterTagRegex();
                              filter.setPattern(m.group(1));
                              filter.setMatchCase(m.group(2) == null || m.group(2).length() == 0);
                              return Optional.of(filter);
                            }),
                    "regular expression"));
    TAG_MATCH.addSymbol(
        "!~",
        (p, o) ->
            p.whitespace()
                .regex(
                    REGEX,
                    m ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTagRegex filter = new ActionFilterTagRegex();
                              filter.setPattern(m.group(1));
                              filter.setMatchCase(m.group(2) == null || m.group(2).length() == 0);
                              filter.setNegate(true);
                              return Optional.of(filter);
                            }),
                    "regular expression"));
    TAG_MATCH.addSymbol(
        "=",
        (p, o) ->
            p.whitespace()
                .dispatch(
                    STRING,
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTag filter = new ActionFilterTag();
                              filter.setTags(new String[] {s});
                              return Optional.of(filter);
                            }))
                .whitespace());
    TAG_MATCH.addSymbol(
        "!=",
        (p, o) ->
            p.whitespace()
                .dispatch(
                    STRING,
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTag filter = new ActionFilterTag();
                              filter.setTags(new String[] {s});
                              filter.setNegate(true);
                              return Optional.of(filter);
                            }))
                .whitespace());
    TAG_MATCH.addSymbol(
        "in",
        (p, o) ->
            p.whitespace()
                .symbol("(")
                .whitespace()
                .list(
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTag filter = new ActionFilterTag();
                              filter.setTags(s.stream().toArray(String[]::new));
                              return Optional.of(filter);
                            }),
                    STRING,
                    ',')
                .symbol(")")
                .whitespace());
    TAG_MATCH.addSymbol(
        "not",
        (p, o) ->
            p.whitespace()
                .keyword("in")
                .whitespace()
                .symbol("(")
                .whitespace()
                .list(
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterTag filter = new ActionFilterTag();
                              filter.setTags(s.stream().toArray(String[]::new));
                              filter.setNegate(true);
                              return Optional.of(filter);
                            }),
                    STRING,
                    ',')
                .symbol(")")
                .whitespace());
    TEXT_MATCH.addSymbol(
        "~",
        (p, o) ->
            p.whitespace()
                .regex(
                    REGEX,
                    m ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterRegex filter = new ActionFilterRegex();
                              filter.setPattern(m.group(1));
                              filter.setMatchCase(m.group(2) == null || m.group(2).length() == 0);
                              return Optional.of(filter);
                            }),
                    "regular expression"));
    TEXT_MATCH.addSymbol(
        "!~",
        (p, o) ->
            p.whitespace()
                .regex(
                    REGEX,
                    m ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterRegex filter = new ActionFilterRegex();
                              filter.setPattern(m.group(1));
                              filter.setMatchCase(m.group(2) == null || m.group(2).length() == 0);
                              filter.setNegate(true);
                              return Optional.of(filter);
                            }),
                    "regular expression"));
    TEXT_MATCH.addSymbol(
        "=",
        (p, o) ->
            p.whitespace()
                .dispatch(
                    STRING,
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterText filter = new ActionFilterText();
                              filter.setText(s);
                              return Optional.of(filter);
                            })));
    TEXT_MATCH.addSymbol(
        "!=",
        (p, o) ->
            p.whitespace()
                .dispatch(
                    STRING,
                    s ->
                        o.accept(
                            errorHandler -> {
                              final ActionFilterText filter = new ActionFilterText();
                              filter.setText(s);
                              filter.setNegate(true);
                              return Optional.of(filter);
                            })));
    SET_MATCH.addSymbol(
        "=",
        (p, o) ->
            p.whitespace()
                .dispatch(STRING, s -> o.accept(new Pair<>(false, Collections.singletonList(s))))
                .whitespace());
    SET_MATCH.addSymbol(
        "!=",
        (p, o) ->
            p.whitespace()
                .dispatch(STRING, s -> o.accept(new Pair<>(true, Collections.singletonList(s))))
                .whitespace());
    SET_MATCH.addSymbol(
        "in",
        (p, o) ->
            p.whitespace()
                .symbol("(")
                .whitespace()
                .list(s -> o.accept(new Pair<>(false, s)), STRING, ',')
                .symbol(")")
                .whitespace());
    SET_MATCH.addSymbol(
        "not",
        (p, o) ->
            p.whitespace()
                .keyword("in")
                .whitespace()
                .symbol("(")
                .whitespace()
                .list(s -> o.accept(new Pair<>(true, s)), STRING, ',')
                .symbol(")")
                .whitespace());
    SOURCE_MATCH.addSymbol(
        "=",
        (p, o) ->
            p.whitespace()
                .then(
                    ActionFilter::parseLocation,
                    s -> o.accept(new Pair<>(false, Collections.singletonList(s))))
                .whitespace());
    SOURCE_MATCH.addSymbol(
        "!=",
        (p, o) ->
            p.whitespace()
                .then(
                    ActionFilter::parseLocation,
                    s -> o.accept(new Pair<>(true, Collections.singletonList(s))))
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
    TEMPORAL.addKeyword("last", (p, o) -> parseAgo(p, o, false));
    TEMPORAL.addKeyword("prior", (p, o) -> parseAgo(p, o, true));
    TEMPORAL.addKeyword("after", (p, o) -> parseOpenRange(p, o, BaseRangeActionFilter::setStart));
    TEMPORAL.addKeyword("before", (p, o) -> parseOpenRange(p, o, BaseRangeActionFilter::setEnd));
    TEMPORAL.addKeyword("between", (p, o) -> parseRange(p, o, false));
    TEMPORAL.addKeyword("outside", (p, o) -> parseRange(p, o, true));
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
    DATE.addKeyword("today", Parser.just(e -> Optional.of(LocalDate.now())));
    DATE.addKeyword("yesterday", Parser.just(e -> Optional.of(LocalDate.now().minusDays(1))));
    for (final DayOfWeek weekday : DayOfWeek.values()) {
      DATE.addKeyword(
          weekday.name().toLowerCase(),
          Parser.just(e -> Optional.of(LocalDate.now().with(weekday))));
    }
    DATE.addRaw(
        "ISO-8601 date",
        (p, o) ->
            p.regex(
                    DATE_PATTERN,
                    m ->
                        o.accept(
                            errorHandler -> {
                              final int year = Integer.parseInt(m.group(1));
                              final int month;
                              final int day = Integer.parseInt(m.group(3));
                              if (Character.isDigit(m.group(2).charAt(0))) {
                                month = Integer.parseInt(m.group(2));
                              } else {
                                final OptionalInt monthValue = parseMonth(m.group(2).toLowerCase());
                                if (monthValue.isPresent()) {
                                  month = monthValue.getAsInt();
                                } else {
                                  errorHandler.raise(
                                      p.line(),
                                      p.column(),
                                      String.format("Unknown month “%s”.", m.group(2)));
                                  return Optional.empty();
                                }
                              }
                              return Optional.of(LocalDate.of(year, month, day));
                            }),
                    "ISO-8601 date")
                .whitespace());
    TIME.addSymbol(
        "current",
        Parser.justWhiteSpace((date, errorHandler) -> Optional.of(date.atTime(LocalTime.now()))));
    TIME.addSymbol(
        "midnight",
        Parser.justWhiteSpace(
            (date, errorHandler) -> Optional.of(date.atTime(LocalTime.MIDNIGHT))));
    TIME.addSymbol(
        "noon",
        Parser.justWhiteSpace((date, errorHandler) -> Optional.of(date.atTime(LocalTime.NOON))));
    TIME.addRaw(
        "ISO-8601 time",
        (p, o) ->
            p.regex(
                    TIME_PATTERN,
                    m ->
                        o.accept(
                            (date, errorHandler) ->
                                Optional.of(
                                    date.atTime(
                                        LocalTime.of(
                                            Integer.parseInt(m.group(1)),
                                            m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2)),
                                            m.group(3).isEmpty()
                                                ? 0
                                                : Integer.parseInt(m.group(3)))))),
                    "ISO-8601 time")
                .whitespace());
    TIME_ZONE.addKeyword(
        "utc",
        Parser.justWhiteSpace(
            (datetime, errorHandler) ->
                Optional.of(datetime.atZone(ZoneId.of("Z")).toInstant().toEpochMilli())));
    TIME_ZONE.addKeyword(
        "server",
        Parser.justWhiteSpace(
            (datetime, errorHandler) ->
                Optional.of(datetime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())));
    TIME_ZONE.addRaw(
        "nothing",
        Parser.justWhiteSpace(
            (datetime, errorHandler) ->
                Optional.of(datetime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())));
  }

  private boolean negate;

  public abstract <F> F convert(ActionFilterBuilder<F> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, ActionFilterBuilder<F> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
