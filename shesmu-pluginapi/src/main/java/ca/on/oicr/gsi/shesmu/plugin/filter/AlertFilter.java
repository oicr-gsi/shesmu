package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.RuleWithLiteral;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AlertFilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = AlertFilterIsLive.class, name = "is_live"),
  @JsonSubTypes.Type(value = AlertFilterLabelName.class, name = "has"),
  @JsonSubTypes.Type(value = AlertFilterLabelValue.class, name = "eq"),
  @JsonSubTypes.Type(value = AlertFilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = AlertFilterSourceLocation.class, name = "sourcelocation")
})
public abstract class AlertFilter {
  private enum LabelValue {
    HAS {
      @Override
      public <S> Parser parse(
          Parser parser,
          S labelName,
          RuleWithLiteral<S, String> string,
          Consumer<AlertFilterNode<S>> output) {
        output.accept(
            new AlertFilterNode<>() {
              @Override
              public <F> Optional<F> generate(
                  AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                return Optional.of(builder.hasLabelName(labelName));
              }
            });
        return parser.whitespace();
      }
    },
    STR {
      @Override
      public <S> Parser parse(
          Parser parser,
          S labelName,
          RuleWithLiteral<S, String> string,
          Consumer<AlertFilterNode<S>> output) {
        return parser
            .then(
                string,
                labelValue ->
                    output.accept(
                        new AlertFilterNode<>() {
                          @Override
                          public <F> Optional<F> generate(
                              AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                            return Optional.of(builder.hasLabelValue(labelName, labelValue));
                          }
                        }))
            .whitespace();
      }
    },
    REGEX {
      @Override
      public <S> Parser parse(
          Parser parser,
          S labelName,
          RuleWithLiteral<S, String> string,
          Consumer<AlertFilterNode<S>> output) {
        final var pattern = new AtomicReference<String>();
        final var result =
            parser
                .whitespace()
                .regex(ActionFilter.REGEX, m -> pattern.set(m.group(1)), "regular expression")
                .whitespace();
        if (result.isGood()) {
          try {
            final var labelValue = Pattern.compile(pattern.get());
            output.accept(
                new AlertFilterNode<>() {
                  @Override
                  public <F> Optional<F> generate(
                      AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                    return Optional.of(builder.hasLabelValue(labelName, labelValue));
                  }
                });
          } catch (PatternSyntaxException e) {
            return result.raise(e.getMessage().split("\n")[0]);
          }
        }
        return result;
      }
    };

    public abstract <S> Parser parse(
        Parser parser,
        S labelName,
        RuleWithLiteral<S, String> string,
        Consumer<AlertFilterNode<S>> output);
  }

  private enum Variable {
    IS_LIVE {
      @Override
      public <S> Parser parse(
          Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
        output.accept(
            new AlertFilterNode<>() {
              @Override
              public <F> Optional<F> generate(
                  AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                return Optional.of(builder.isLive());
              }
            });
        return parser;
      }
    },
    VALUE {
      @Override
      public <S> Parser parse(
          Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
        final var labelName = new AtomicReference<S>();
        final var value = new AtomicReference<LabelValue>();
        final var result =
            parser
                .whitespace()
                .then(string, labelName::set)
                .whitespace()
                .symbol("]")
                .whitespace()
                .dispatch(LABEL_VALUE, value::set);
        if (result.isGood()) {
          return value.get().parse(result, labelName.get(), string, output);
        }
        return parser;
      }
    },

    LABEL_REGEX {
      @Override
      public <S> Parser parse(
          Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
        final var pattern = new AtomicReference<String>();
        final var result =
            parser
                .whitespace()
                .regex(ActionFilter.REGEX, m -> pattern.set(m.group(1)), "regular expression")
                .whitespace();
        if (result.isGood()) {
          try {
            final var labelName = Pattern.compile(pattern.get());
            output.accept(
                new AlertFilterNode<>() {
                  @Override
                  public <F> Optional<F> generate(
                      AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                    return Optional.of(builder.hasLabelName(labelName));
                  }
                });
          } catch (PatternSyntaxException e) {
            return result.raise(e.getMessage().split("\n")[0]);
          }
        }
        return result;
      }
    };

    public abstract <S> Parser parse(
        Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output);
  }

  public interface AlertFilterNode<S> {
    <F> Optional<F> generate(AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler);
  }

  private interface BinaryConstructor {
    <F> F create(AlertFilterBuilder<F, ?> builder, Stream<F> filters);
  }

  private static final Parser.ParseDispatch<LabelValue> LABEL_VALUE = new ParseDispatch<>();
  private static final Parser.ParseDispatch<Variable> VARIABLE = new ParseDispatch<>();

  static {
    VARIABLE.addKeyword("LIVE", Parser.justWhiteSpace(Variable.IS_LIVE));
    // TODO: this needs good VARIABLE.addKeyword("@~", Parser.justWhiteSpace(Variable.LABEL_REGEX));
    VARIABLE.addSymbol("[", Parser.justWhiteSpace(Variable.VALUE));
    LABEL_VALUE.addSymbol(
        "=",
        (p, o) -> {
          final var anyResult = p.whitespace().symbol("*").whitespace();
          if (anyResult.isGood()) {
            o.accept(LabelValue.HAS);
            return anyResult;
          }
          o.accept(LabelValue.STR);
          return p.whitespace();
        });
    LABEL_VALUE.addSymbol("~", Parser.justWhiteSpace(LabelValue.REGEX));
  }

  private static <S> Parser.Rule<BinaryOperator<AlertFilterNode<S>>> binary(
      String keyword, BinaryConstructor constructor) {
    return (p, o) -> {
      final var result = p.keyword(keyword).whitespace();
      if (result.isGood()) {
        o.accept(
            (a, b) ->
                new AlertFilterNode<>() {
                  @Override
                  public <F> Optional<F> generate(
                      AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                    final var aValue = a.generate(builder, errorHandler);
                    final var bValue = b.generate(builder, errorHandler);
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

  public static <S> Parser parse(
      Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
    return Parser.scanBinary(
        (p, o) -> parse1(p, string, o),
        binary(
            "|",
            new BinaryConstructor() {
              @Override
              public <F> F create(AlertFilterBuilder<F, ?> builder, Stream<F> filters) {
                return builder.or(filters);
              }
            }),
        parser,
        output);
  }

  private static <S> Parser parse1(
      Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
    return Parser.scanBinary(
        (p, o) -> parse2(p, string, o),
        binary(
            "&",
            new BinaryConstructor() {
              @Override
              public <F> F create(AlertFilterBuilder<F, ?> builder, Stream<F> filters) {
                return builder.and(filters);
              }
            }),
        parser,
        output);
  }

  private static <S> Parser parse2(
      Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {
    return Parser.scanPrefixed(
        (p, o) -> parse3(p, string, o),
        (p, o) -> {
          final var result = p.keyword("!").whitespace();
          if (result.isGood()) {
            o.accept(
                node ->
                    new AlertFilterNode<>() {
                      @Override
                      public <F> Optional<F> generate(
                          AlertFilterBuilder<F, S> builder, ErrorConsumer errorHandler) {
                        return node.generate(builder, errorHandler).map(builder::negate);
                      }
                    });
          }
          return result;
        },
        parser,
        output);
  }

  private static <S> Parser parse3(
      Parser parser, RuleWithLiteral<S, String> string, Consumer<AlertFilterNode<S>> output) {

    final var subExpressionParser = parser.symbol("(");
    if (subExpressionParser.isGood()) {
      return parse(subExpressionParser.whitespace(), string, output).symbol(")").whitespace();
    }
    final var variable = new AtomicReference<Variable>();
    final var result = parser.dispatch(VARIABLE, variable::set).whitespace();
    if (result.isGood()) {
      return variable.get().parse(result, string, output).whitespace();
    }
    return result;
  }

  private boolean negate;

  public abstract <F> F convert(AlertFilterBuilder<F, String> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, AlertFilterBuilder<F, String> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
