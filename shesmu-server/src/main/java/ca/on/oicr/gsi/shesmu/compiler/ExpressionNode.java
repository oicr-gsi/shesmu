package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.Renderable;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** An expression in the Shesmu language */
public abstract class ExpressionNode implements Renderable {

  interface UnaryExpression {
    ExpressionNode create(int line, int column, ExpressionNode node);
  }

  private static Parser.Rule<BinaryOperator<ExpressionNode>> binaryOperators(
      String symbol, BinaryOperation.Definition... operations) {
    return (p, o) -> {
      o.accept(
          (l, r) ->
              new ExpressionNodeBinary(
                  () -> Stream.of(operations), symbol, p.line(), p.column(), l, r));
      return p;
    };
  }

  private static Optional<Pair<String, Boolean>> generateRecursiveError(
      Imyhat acceptable, Imyhat found, boolean root) {
    if (acceptable.isSame(found)) return Optional.empty();
    if (acceptable instanceof Imyhat.ObjectImyhat && found instanceof Imyhat.ObjectImyhat) {
      final List<String> errors = new ArrayList<>();
      final Map<String, Imyhat> acceptableFields =
          ((Imyhat.ObjectImyhat) acceptable)
              .fields()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().first()));
      final Map<String, Imyhat> foundFields =
          ((Imyhat.ObjectImyhat) found)
              .fields()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().first()));
      final Set<String> union = new TreeSet<>(acceptableFields.keySet());
      union.addAll(foundFields.keySet());
      for (final String field : union) {
        final Imyhat acceptableFieldType = acceptableFields.get(field);
        final Imyhat foundFieldType = foundFields.get(field);
        if (acceptableFieldType == null) {
          errors.add(String.format("extra %s", field));
        } else if (foundFieldType == null) {
          errors.add(String.format("missing %s", field));
        } else {
          generateRecursiveError(acceptableFieldType, foundFieldType, false)
              .ifPresent(error -> errors.add(String.format("%s = %s", field, error.first())));
        }
      }
      return errors.isEmpty()
          ? Optional.empty()
          : Optional.of(new Pair<>("{ " + String.join(", ", errors) + " }", false));
    }
    if (acceptable instanceof Imyhat.ListImyhat && found instanceof Imyhat.ListImyhat) {
      return generateRecursiveError(
              ((Imyhat.ListImyhat) acceptable).inner(), ((Imyhat.ListImyhat) found).inner(), false)
          .map(p -> new Pair<>("[" + p.first() + "]", false));
    }
    if (acceptable instanceof Imyhat.OptionalImyhat && found instanceof Imyhat.OptionalImyhat) {
      return generateRecursiveError(
              ((Imyhat.OptionalImyhat) acceptable).inner(),
              ((Imyhat.OptionalImyhat) found).inner(),
              false)
          .map(p -> new Pair<>(p.second() ? ("(" + p.first() + ")?") : (p.first() + "?"), false));
    }
    if (acceptable instanceof Imyhat.TupleImyhat && found instanceof Imyhat.TupleImyhat) {
      final List<Imyhat> acceptableElements =
          ((Imyhat.TupleImyhat) acceptable).inner().collect(Collectors.toList());
      final List<Imyhat> foundElements =
          ((Imyhat.TupleImyhat) found).inner().collect(Collectors.toList());
      final List<String> errors = new ArrayList<>();
      for (int index = 0;
          index < Math.min(acceptableElements.size(), foundElements.size());
          index++) {
        final Imyhat acceptableFieldType = acceptableElements.get(index);
        final Imyhat foundFieldType = foundElements.get(index);
        errors.add(
            generateRecursiveError(acceptableFieldType, foundFieldType, false)
                .map(Pair::first)
                .orElse("_"));
      }
      while (errors.size() < acceptableElements.size()) {
        errors.add("missing");
      }
      while (errors.size() < foundElements.size()) {
        errors.add("extra");
      }

      return errors.isEmpty()
          ? Optional.empty()
          : Optional.of(new Pair<>("{ " + String.join(", ", errors) + " }", false));
    }
    return root
        ? Optional.empty()
        : Optional.of(new Pair<>(String.format("%s vs %s", acceptable.name(), found.name()), true));
  }

  static void generateTypeError(
      int line,
      int column,
      String context,
      Imyhat acceptable,
      Imyhat found,
      Consumer<String> errorHandler) {
    final Optional<String> recursiveError =
        generateRecursiveError(acceptable, found, true).map(Pair::first);
    if (recursiveError.isPresent()) {
      errorHandler.accept(
          String.format("%d:%d: Type mismatch%s %s.", line, column, context, recursiveError.get()));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Expected %s%s, but got %s.",
              line, column, acceptable.name(), context, found.name()));
    }
  }

  private static <T> Parser.Rule<T> just(T value) {
    return (p, o) -> {
      o.accept(value);
      return p;
    };
  }

  private static Parser.Rule<UnaryOperator<ExpressionNode>> just(UnaryExpression creator) {
    return (p, o) -> {
      o.accept(n -> creator.create(p.line(), p.column(), n));
      return p;
    };
  }

  public static Parser parse(Parser input, Consumer<ExpressionNode> output) {
    return scanBinary(ExpressionNode::parse0, COALESCING, input, output);
  }

  public static Parser parse0(Parser input, Consumer<ExpressionNode> output) {
    return input.dispatch(OUTER, output);
  }

  private static Parser parse1(Parser input, Consumer<ExpressionNode> output) {
    return scanBinary(ExpressionNode::parse2, LOGICAL_DISJUNCTION, input, output);
  }

  private static Parser parse2(Parser input, Consumer<ExpressionNode> output) {
    return scanBinary(ExpressionNode::parse3, LOGICAL_CONJUNCTION, input, output);
  }

  private static Parser parse3(Parser input, Consumer<ExpressionNode> output) {
    return scanSuffixed(ExpressionNode::parse4, COMPARISON, false, input, output);
  }

  private static Parser parse4(Parser input, Consumer<ExpressionNode> output) {
    return scanBinary(ExpressionNode::parse5, DISJUNCTION, input, output);
  }

  private static Parser parse5(Parser input, Consumer<ExpressionNode> output) {
    return scanBinary(ExpressionNode::parse6, CONJUNCTION, input, output);
  }

  private static Parser parse6(Parser input, Consumer<ExpressionNode> output) {
    return scanSuffixed(ExpressionNode::parse7, SUFFIX_LOOSE, false, input, output);
  }

  private static Parser parse7(Parser input, Consumer<ExpressionNode> output) {
    return scanPrefixed(ExpressionNode::parse8, UNARY, input, output);
  }

  private static Parser parse8(Parser input, Consumer<ExpressionNode> output) {
    return scanSuffixed(ExpressionNode::parse9, SUFFIX_TIGHT, true, input, output);
  }

  private static Parser parse9(Parser input, Consumer<ExpressionNode> output) {
    return input.dispatch(TERMINAL, output);
  }

  private static <T> Parser scanBinary(
      Parser.Rule<T> child,
      Parser.ParseDispatch<BinaryOperator<T>> condensers,
      Parser input,
      Consumer<T> output) {
    final AtomicReference<T> node = new AtomicReference<>();
    Parser parser = child.parse(input, node::set);
    while (parser.isGood()) {
      final AtomicReference<T> right = new AtomicReference<>();
      final AtomicReference<BinaryOperator<T>> condenser = new AtomicReference<>();
      final Parser next =
          child.parse(parser.dispatch(condensers, condenser::set).whitespace(), right::set);
      if (next.isGood()) {
        node.set(condenser.get().apply(node.get(), right.get()));
        parser = next;
      } else {
        output.accept(node.get());
        return parser;
      }
    }
    return parser;
  }

  private static <T> Parser scanPrefixed(
      Parser.Rule<T> child,
      Parser.ParseDispatch<UnaryOperator<T>> condensers,
      Parser input,
      Consumer<T> output) {
    final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
    Parser next = input.dispatch(condensers, modifier::set).whitespace();
    if (!next.isGood()) {
      next = input;
      modifier.set(UnaryOperator.identity());
    }
    final AtomicReference<T> node = new AtomicReference<>();
    final Parser result = child.parse(next, node::set).whitespace();
    if (result.isGood()) {
      output.accept(modifier.get().apply(node.get()));
    }
    return result;
  }

  private static <T> Parser scanSuffixed(
      Parser.Rule<T> child,
      Parser.ParseDispatch<UnaryOperator<T>> condensers,
      boolean repeated,
      Parser input,
      Consumer<T> output) {
    final AtomicReference<T> node = new AtomicReference<>();
    Parser result = child.parse(input, node::set).whitespace();
    boolean again = true;
    while (result.isGood() && again) {
      final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
      final Parser next = result.dispatch(condensers, modifier::set).whitespace();
      if (next.isGood()) {
        result = next;
        node.updateAndGet(modifier.get());
        again = repeated;
      } else {
        again = false;
      }
    }
    if (result.isGood()) {
      output.accept(node.get());
    }
    return result;
  }

  private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> COALESCING =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> COMPARISON =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> CONJUNCTION =
      new Parser.ParseDispatch<>();
  private static final Pattern DATE =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}))?");
  private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> DISJUNCTION =
      new Parser.ParseDispatch<>();
  private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d+\\.\\d*([eE][+-]?\\d+)?");
  public static final Parser.ParseDispatch<Integer> INT_SUFFIX = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> LOGICAL_CONJUNCTION =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<BinaryOperator<ExpressionNode>> LOGICAL_DISJUNCTION =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<ExpressionNode> OUTER = new Parser.ParseDispatch<>();
  private static final Pattern PATH = Pattern.compile("^([^\\\\']|\\\\')+");
  public static final Pattern REGEX = Pattern.compile("^/(([^\\\\/\n]|\\\\.)*)/");
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> SUFFIX_LOOSE =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> SUFFIX_TIGHT =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<ExpressionNode> TERMINAL = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> UNARY =
      new Parser.ParseDispatch<>();

  static {
    final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);

    INT_SUFFIX.addKeyword("Gi", just(1024 * 1024 * 1024));
    INT_SUFFIX.addKeyword("Mi", just(1024 * 1024));
    INT_SUFFIX.addKeyword("ki", just(1024));
    INT_SUFFIX.addKeyword("G", just(1000 * 1000 * 1000));
    INT_SUFFIX.addKeyword("M", just(1000 * 1000));
    INT_SUFFIX.addKeyword("k", just(1000));
    INT_SUFFIX.addKeyword("weeks", just(3600 * 24 * 7));
    INT_SUFFIX.addKeyword("days", just(3600 * 24));
    INT_SUFFIX.addKeyword("hours", just(3600));
    INT_SUFFIX.addKeyword("mins", just(60));
    INT_SUFFIX.addKeyword("", just(1));
    COALESCING.addKeyword("Default", binaryOperators("Default", BinaryOperation::optionalCoalesce));
    OUTER.addKeyword(
        "If",
        (p, o) -> {
          final AtomicReference<ExpressionNode> testExpression = new AtomicReference<>();
          final AtomicReference<ExpressionNode> trueExpression = new AtomicReference<>();
          final AtomicReference<ExpressionNode> falseExpression = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, testExpression::set)
                  .whitespace()
                  .keyword("Then")
                  .whitespace()
                  .then(ExpressionNode::parse, trueExpression::set)
                  .whitespace()
                  .keyword("Else")
                  .whitespace()
                  .then(ExpressionNode::parse0, falseExpression::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeTernaryIf(
                    p.line(),
                    p.column(),
                    testExpression.get(),
                    trueExpression.get(),
                    falseExpression.get()));
          }
          return result;
        });
    OUTER.addKeyword(
        "Switch",
        (p, o) -> {
          final AtomicReference<List<Pair<ExpressionNode, ExpressionNode>>> cases =
              new AtomicReference<>();
          final AtomicReference<ExpressionNode> test = new AtomicReference<>();
          final AtomicReference<ExpressionNode> alternative = new AtomicReference<>();
          final Parser result =
              parse1(
                      parse(p.whitespace(), test::set)
                          .whitespace()
                          .list(
                              cases::set,
                              (cp, co) -> {
                                final AtomicReference<ExpressionNode> condition =
                                    new AtomicReference<>();
                                final AtomicReference<ExpressionNode> value =
                                    new AtomicReference<>();
                                final Parser cresult =
                                    parse(
                                        parse(
                                                cp.whitespace().keyword("When").whitespace(),
                                                condition::set)
                                            .whitespace()
                                            .keyword("Then")
                                            .whitespace(),
                                        value::set);
                                if (cresult.isGood()) {
                                  co.accept(new Pair<>(condition.get(), value.get()));
                                }
                                return cresult;
                              })
                          .whitespace()
                          .keyword("Else")
                          .whitespace(),
                      alternative::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeSwitch(
                    p.line(), p.column(), test.get(), cases.get(), alternative.get()));
          }
          return result;
        });
    OUTER.addKeyword(
        "For",
        (p, o) -> {
          final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
          final AtomicReference<SourceNode> source = new AtomicReference<>();
          final AtomicReference<List<ListNode>> transforms = new AtomicReference<>();
          final AtomicReference<CollectNode> collector = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .then(SourceNode::parse, source::set)
                  .whitespace()
                  .symbol(":")
                  .whitespace()
                  .list(transforms::set, ListNode::parse)
                  .then(CollectNode::parse, collector::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeFor(
                    p.line(),
                    p.column(),
                    name.get(),
                    source.get(),
                    transforms.get(),
                    collector.get()));
          }
          return result;
        });
    OUTER.addKeyword(
        "Begin",
        (p, o) -> {
          final AtomicReference<List<Pair<DestructuredArgumentNode, ExpressionNode>>> definitions =
              new AtomicReference<>();
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .list(
                      definitions::set,
                      (defp, defo) -> {
                        final AtomicReference<DestructuredArgumentNode> name =
                            new AtomicReference<>();
                        final AtomicReference<ExpressionNode> expr = new AtomicReference<>();
                        final Parser defResult =
                            defp.whitespace()
                                .then(DestructuredArgumentNode::parse, name::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .then(ExpressionNode::parse, expr::set)
                                .whitespace()
                                .symbol(";")
                                .whitespace();
                        if (defResult.isGood()) {
                          defo.accept(new Pair<>(name.get(), expr.get()));
                        }
                        return defResult;
                      })
                  .whitespace()
                  .keyword("Return")
                  .whitespace()
                  .then(ExpressionNode::parse0, expression::set)
                  .symbol(";")
                  .whitespace()
                  .keyword("End")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeBlock(p.line(), p.column(), definitions.get(), expression.get()));
          }
          return result;
        });
    OUTER.addRaw("expression", ExpressionNode::parse1);
    LOGICAL_DISJUNCTION.addSymbol(
        "||",
        binaryOperators(
            "||",
            BinaryOperation.shortCircuit(GeneratorAdapter.NE),
            BinaryOperation::optionalMerge));

    LOGICAL_CONJUNCTION.addSymbol(
        "&&", binaryOperators("&&", BinaryOperation.shortCircuit(GeneratorAdapter.EQ)));

    for (final Comparison comparison : Comparison.values()) {
      COMPARISON.addSymbol(
          comparison.symbol(),
          (p, o) -> {
            final AtomicReference<ExpressionNode> right = new AtomicReference<>();
            final Parser result = parse4(p.whitespace(), right::set).whitespace();
            if (result.isGood()) {

              o.accept(
                  left ->
                      new ExpressionNodeComparison(
                          p.line(), p.column(), comparison, left, right.get()));
            }
            return result;
          });
    }
    COMPARISON.addSymbol(
        "~",
        (p, o) -> {
          final AtomicReference<String> regex = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .regex(REGEX, m -> regex.set(m.group(1)), "Regular expression.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(left -> new ExpressionNodeRegex(p.line(), p.column(), left, regex.get()));
          }
          return result;
        });
    COMPARISON.addSymbol(
        "!~",
        (p, o) -> {
          final AtomicReference<String> regex = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .regex(REGEX, m -> regex.set(m.group(1)), "Regular expression.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                left ->
                    new ExpressionNodeLogicalNot(
                        p.line(),
                        p.column(),
                        new ExpressionNodeRegex(p.line(), p.column(), left, regex.get())));
          }
          return result;
        });
    DISJUNCTION.addSymbol(
        "+",
        binaryOperators(
            "+",
            BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.ADD),
            BinaryOperation.virtualMethod(Imyhat.DATE, Imyhat.INTEGER, Imyhat.DATE, "plusSeconds"),
            BinaryOperation.virtualMethod(Imyhat.PATH, Imyhat.PATH, Imyhat.PATH, "resolve"),
            BinaryOperation.staticMethod(
                Imyhat.PATH, Imyhat.STRING, Imyhat.PATH, A_RUNTIME_SUPPORT_TYPE, "resolvePath"),
            BinaryOperation.binaryListStaticMethod(A_RUNTIME_SUPPORT_TYPE, "union"),
            BinaryOperation.listAndItemStaticMethod(A_RUNTIME_SUPPORT_TYPE, "addItem"),
            BinaryOperation::tupleConcat,
            BinaryOperation::objectConcat));
    DISJUNCTION.addSymbol(
        "-",
        binaryOperators(
            "-",
            BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.SUB),
            BinaryOperation.virtualMethod(Imyhat.DATE, Imyhat.INTEGER, Imyhat.DATE, "minusSeconds"),
            BinaryOperation.staticMethod(
                Imyhat.DATE, Imyhat.DATE, Imyhat.INTEGER, A_RUNTIME_SUPPORT_TYPE, "difference"),
            BinaryOperation.binaryListStaticMethod(A_RUNTIME_SUPPORT_TYPE, "difference"),
            BinaryOperation.listAndItemStaticMethod(A_RUNTIME_SUPPORT_TYPE, "removeItem")));

    CONJUNCTION.addSymbol(
        "*", binaryOperators("*", BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.MUL)));
    CONJUNCTION.addSymbol(
        "/", binaryOperators("/", BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.DIV)));
    CONJUNCTION.addSymbol(
        "%",
        binaryOperators("%", BinaryOperation.primitiveMath(Imyhat.INTEGER, GeneratorAdapter.REM)));

    SUFFIX_LOOSE.addSymbol(
        "?",
        (p, o) -> {
          o.accept(node -> new ExpressionNodeOptionalUnbox(p.line(), p.column(), node));
          return p;
        });
    SUFFIX_LOOSE.addKeyword(
        "In",
        (p, o) -> {
          final AtomicReference<ExpressionNode> collection = new AtomicReference<>();
          final Parser result = parse7(p.whitespace(), collection::set);
          if (result.isGood()) {
            final ExpressionNode c = collection.get();
            o.accept(node -> new ExpressionNodeContains(p.line(), p.column(), node, c));
          }
          return result;
        });
    SUFFIX_LOOSE.addKeyword(
        "As",
        (p, o) -> {
          final Parser jsonParser = p.whitespace().keyword("json").whitespace();
          if (jsonParser.isGood()) {
            o.accept(node -> new ExpressionNodeJsonPack(p.line(), p.column(), node));
            return jsonParser;
          }
          final AtomicReference<ImyhatNode> typeNode = new AtomicReference<>();
          final Parser result = p.whitespace().then(ImyhatNode::parse, typeNode::set).whitespace();
          if (result.isGood()) {
            final ImyhatNode type = typeNode.get();
            o.accept(node -> new ExpressionNodeJsonUnpack(p.line(), p.column(), node, type));
          }
          return result;
        });

    UNARY.addSymbol("!", just(ExpressionNodeLogicalNot::new));
    UNARY.addSymbol("-", just(ExpressionNodeNegate::new));

    SUFFIX_TIGHT.addSymbol(
        "[",
        (p, o) -> {
          final AtomicLong index = new AtomicLong();
          final Parser result =
              p.whitespace().integer(index::set, 10).whitespace().symbol("]").whitespace();
          if (result.isGood()) {
            final int i = (int) index.get();
            o.accept(node -> new ExpressionNodeTupleGet(p.line(), p.column(), node, i));
          }
          return result;
        });
    SUFFIX_TIGHT.addSymbol(
        ".",
        (p, o) -> {
          final AtomicReference<String> index = new AtomicReference<>();
          final Parser result = p.whitespace().identifier(index::set).whitespace();
          if (result.isGood()) {
            o.accept(node -> new ExpressionNodeObjectGet(p.line(), p.column(), node, index.get()));
          }
          return result;
        });

    TERMINAL.addKeyword(
        "Date",
        (p, o) ->
            p.whitespace()
                .regex(
                    DATE,
                    m -> {
                      ZonedDateTime date;
                      if (m.start(1) == m.end(1)) {
                        date = LocalDate.parse(m.group(0)).atStartOfDay(ZoneId.of("Z"));
                      } else if (m.start(2) == m.end(2)) {
                        date = LocalDateTime.parse(m.group(0)).atZone(ZoneId.of("Z"));
                      } else {
                        date = ZonedDateTime.parse(m.group(0));
                      }
                      o.accept(new ExpressionNodeDate(p.line(), p.column(), date));
                    },
                    "Expected date.")
                .whitespace());
    TERMINAL.addKeyword(
        "EpochMilli",
        (p, o) ->
            p.whitespace()
                .integer(
                    e -> {
                      o.accept(
                          new ExpressionNodeDate(p.line(), p.column(), Instant.ofEpochMilli(e)));
                    },
                    10)
                .whitespace());
    TERMINAL.addKeyword(
        "EpochSecond",
        (p, o) ->
            p.whitespace()
                .integer(
                    e -> {
                      o.accept(
                          new ExpressionNodeDate(p.line(), p.column(), Instant.ofEpochSecond(e)));
                    },
                    10)
                .whitespace());
    TERMINAL.addSymbol(
        "{",
        (p, o) -> {
          final Parser gangParser = p.whitespace().symbol("@");
          if (gangParser.isGood()) {
            final AtomicReference<String> name = new AtomicReference<>();
            final Parser gangResult =
                gangParser.whitespace().identifier(name::set).whitespace().symbol("}").whitespace();
            if (gangResult.isGood()) {
              o.accept(new ExpressionNodeGangTuple(p.line(), p.column(), name.get()));
            }
            return gangResult;
          }
          final AtomicReference<List<Pair<String, ExpressionNode>>> fields =
              new AtomicReference<>();
          final Parser objectResult =
              p.whitespace()
                  .<Pair<String, ExpressionNode>>list(
                      fields::set,
                      (fp, fo) -> {
                        final AtomicReference<String> name = new AtomicReference<>();
                        final AtomicReference<ExpressionNode> value = new AtomicReference<>();
                        final Parser fresult =
                            fp.whitespace()
                                .identifier(name::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .then(ExpressionNode::parse, value::set);
                        if (fresult.isGood()) {
                          fo.accept(new Pair<>(name.get(), value.get()));
                        }
                        return fresult;
                      },
                      ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();
          if (objectResult.isGood()) {
            o.accept(new ExpressionNodeObject(p.line(), p.column(), fields.get()));
            return objectResult;
          }

          final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .list(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeTuple(p.line(), p.column(), items.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "[",
        (p, o) -> {
          final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .listEmpty(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')
                  .whitespace()
                  .symbol("]")
                  .whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeList(p.line(), p.column(), items.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "\"",
        (p, o) -> {
          final AtomicReference<List<StringNode>> items = new AtomicReference<>();
          final Parser result = p.list(items::set, StringNode::parse).symbol("\"").whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeString(p.line(), p.column(), items.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "'",
        (p, o) -> {
          final AtomicReference<String> path = new AtomicReference<>();
          final Parser result =
              p.regex(PATH, m -> path.set(m.group(0).replace("\\'", "'")), "Expected path.")
                  .symbol("'")
                  .whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodePathLiteral(p.line(), p.column(), path.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "`",
        (p, o) -> {
          final AtomicReference<ExpressionNode> value = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, value::set)
                  .whitespace()
                  .symbol("`")
                  .whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeOptionalOf(p.line(), p.column(), value.get()));
          }
          final Parser emptyResult = p.whitespace().symbol("`").whitespace();
          if (emptyResult.isGood()) {
            o.accept(new ExpressionNodeOptionalEmpty(p.line(), p.column()));
            return emptyResult;
          }
          return result;
        });
    TERMINAL.addSymbol(
        "(",
        (p, o) -> {
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              parse(p.whitespace(), expression::set).whitespace().symbol(")").whitespace();
          if (result.isGood()) {
            o.accept(expression.get());
          }
          return result;
        });
    TERMINAL.addRaw(
        "floating-point number",
        (p, o) -> {
          final AtomicReference<Double> value = new AtomicReference<>();
          final Parser result =
              p.regex(
                      DOUBLE_PATTERN,
                      m -> value.set(Double.parseDouble(m.group(0))),
                      "Expected double.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new ExpressionNodeDouble(p.line(), p.column(), value.get()));
          }
          return result;
        });
    TERMINAL.addRaw(
        "integer",
        (p, o) -> {
          final AtomicLong value = new AtomicLong();
          final AtomicInteger multiplier = new AtomicInteger();
          final Parser result =
              p.integer(value::set, 10).dispatch(INT_SUFFIX, multiplier::set).whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeInteger(p.line(), p.column(), value.get() * multiplier.get()));
          }
          return result;
        });
    TERMINAL.addKeyword(
        "True",
        (p, o) -> {
          o.accept(new ExpressionNodeBoolean(p.line(), p.column(), true));
          return p;
        });
    TERMINAL.addKeyword(
        "False",
        (p, o) -> {
          o.accept(new ExpressionNodeBoolean(p.line(), p.column(), false));
          return p;
        });
    TERMINAL.addRaw(
        "function call, variable",
        (p, o) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          Parser result = p.identifier(name::set).whitespace();
          if (result.isGood()) {
            if (result.lookAhead('(')) {
              final AtomicReference<List<ExpressionNode>> items = new AtomicReference<>();
              result =
                  result
                      .symbol("(")
                      .whitespace()
                      .list(items::set, (cp, co) -> parse(cp.whitespace(), co).whitespace(), ',')
                      .whitespace()
                      .symbol(")")
                      .whitespace();
              if (p.isGood()) {
                o.accept(
                    new ExpressionNodeFunctionCall(p.line(), p.column(), name.get(), items.get()));
              }
            } else {
              o.accept(new ExpressionNodeVariable(p.line(), p.column(), name.get()));
            }
          }
          return result;
        });
  }

  private final int column;
  private final int line;

  public ExpressionNode(int line, int column) {
    super();
    this.line = line;
    this.column = column;
  }

  /** Add all free variable names to the set provided. */
  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public int column() {
    return column;
  }

  public int line() {
    return line;
  }

  /** Produce bytecode for this expression */
  public abstract void render(Renderer renderer);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all function plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  /**
   * The type of this expression
   *
   * <p>This should return {@link Imyhat#BAD} if no type can be determined
   */
  public abstract Imyhat type();

  /** Perform type checking on this expression and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler);

  /**
   * Convenience function to produce a type error
   *
   * @param acceptable the allowed type
   * @param found the type provided
   */
  protected void typeError(Imyhat acceptable, Imyhat found, Consumer<String> errorHandler) {
    generateTypeError(line(), column(), "", acceptable, found, errorHandler);
  }

  /**
   * Convenience function to produce a type error
   *
   * @param acceptable the allowed type
   * @param found the type provided
   */
  protected void typeError(String acceptable, Imyhat found, Consumer<String> errorHandler) {
    errorHandler.accept(
        String.format(
            "%d:%d: Expected %s, but got %s.", line(), column(), acceptable, found.name()));
  }
}
