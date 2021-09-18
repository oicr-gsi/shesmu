package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.Renderable;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.AlgebraicImyhat;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
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
      final var acceptableFields =
          ((Imyhat.ObjectImyhat) acceptable)
              .fields()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().first()));
      final var foundFields =
          ((Imyhat.ObjectImyhat) found)
              .fields()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().first()));
      final Set<String> union = new TreeSet<>(acceptableFields.keySet());
      union.addAll(foundFields.keySet());
      for (final var field : union) {
        final var acceptableFieldType = acceptableFields.get(field);
        final var foundFieldType = foundFields.get(field);
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
      final var acceptableElements =
          ((Imyhat.TupleImyhat) acceptable).inner().collect(Collectors.toList());
      final var foundElements = ((Imyhat.TupleImyhat) found).inner().collect(Collectors.toList());
      final List<String> errors = new ArrayList<>();
      for (var index = 0;
          index < Math.min(acceptableElements.size(), foundElements.size());
          index++) {
        final var acceptableFieldType = acceptableElements.get(index);
        final var foundFieldType = foundElements.get(index);
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
    if (acceptable instanceof AlgebraicImyhat && found instanceof AlgebraicImyhat) {
      final var acceptableType = ((AlgebraicImyhat) acceptable);
      final var foundType = ((AlgebraicImyhat) found);
      final var errors =
          foundType
              .members()
              .flatMap(
                  foundName -> {
                    final var acceptableElement = acceptableType.typeFor(foundName);
                    if (acceptableElement.isBad()) {
                      return Stream.of(
                          String.format(
                              "%s is provided but %s is required",
                              foundName,
                              acceptableType.members().collect(Collectors.joining(" or "))));
                    }
                    return generateRecursiveError(
                        acceptableElement, foundType.typeFor(foundName), false)
                        .map(
                            p -> {
                              if (p.second()) {
                                return "(" + foundName + " " + p.first() + ")";
                              } else {
                                return foundName + " " + p.first();
                              }
                            })
                        .stream();
                  })
              .collect(Collectors.toList());
      return errors.isEmpty()
          ? Optional.empty()
          : Optional.of(new Pair<>(String.join(" | ", errors), true));
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
    final var recursiveError = generateRecursiveError(acceptable, found, true).map(Pair::first);
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

  private static Parser.Rule<UnaryOperator<ExpressionNode>> just(UnaryExpression creator) {
    return (p, o) -> {
      o.accept(n -> creator.create(p.line(), p.column(), n));
      return p;
    };
  }

  public static Parser parse(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanBinary(ExpressionNode::parse0, COALESCING, input.whitespace(), output)
        .whitespace();
  }

  public static Parser parse0(Parser input, Consumer<ExpressionNode> output) {
    return input.dispatch(OUTER, output);
  }

  private static Parser parse1(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanBinary(ExpressionNode::parse2, LOGICAL_DISJUNCTION, input, output);
  }

  private static Parser parse2(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanBinary(ExpressionNode::parse3, LOGICAL_CONJUNCTION, input, output);
  }

  private static Parser parse3(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanSuffixed(ExpressionNode::parse4, COMPARISON, false, input, output);
  }

  private static Parser parse4(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanBinary(ExpressionNode::parse5, DISJUNCTION, input, output);
  }

  private static Parser parse5(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanBinary(ExpressionNode::parse6, CONJUNCTION, input, output);
  }

  private static Parser parse6(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanSuffixed(ExpressionNode::parse7, SUFFIX_LOOSE, false, input, output);
  }

  private static Parser parse7(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanPrefixed(ExpressionNode::parse8, UNARY, input, output);
  }

  private static Parser parse8(Parser input, Consumer<ExpressionNode> output) {
    return Parser.scanSuffixed(ExpressionNode::parse9, SUFFIX_TIGHT, true, input, output);
  }

  private static Parser parse9(Parser input, Consumer<ExpressionNode> output) {
    return input.dispatch(TERMINAL, output);
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
  private static final Parser.ParseDispatch<String> PATH = new Parser.ParseDispatch<>();
  private static final Pattern PATH_CHUNK = Pattern.compile("^[^\\\\'\n]+");
  public static final Pattern REGEX = Pattern.compile("^/((?:[^\\\\/\n]|\\\\.)*)/([imsu]*)");
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> SUFFIX_LOOSE =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> SUFFIX_TIGHT =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<ExpressionNode> TERMINAL = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<UnaryOperator<ExpressionNode>> UNARY =
      new Parser.ParseDispatch<>();

  static {
    final var A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);

    INT_SUFFIX.addKeyword("Gi", Parser.just(1024 * 1024 * 1024));
    INT_SUFFIX.addKeyword("Mi", Parser.just(1024 * 1024));
    INT_SUFFIX.addKeyword("ki", Parser.just(1024));
    INT_SUFFIX.addKeyword("G", Parser.just(1000 * 1000 * 1000));
    INT_SUFFIX.addKeyword("M", Parser.just(1000 * 1000));
    INT_SUFFIX.addKeyword("k", Parser.just(1000));
    INT_SUFFIX.addKeyword("weeks", Parser.just(3600 * 24 * 7));
    INT_SUFFIX.addKeyword("days", Parser.just(3600 * 24));
    INT_SUFFIX.addKeyword("hours", Parser.just(3600));
    INT_SUFFIX.addKeyword("mins", Parser.just(60));
    INT_SUFFIX.addKeyword("", Parser.just(1));
    COALESCING.addKeyword("Default", binaryOperators("Default", BinaryOperation::optionalCoalesce));
    OUTER.addKeyword(
        "IfDefined",
        (p, o) -> {
          final var tests = new AtomicReference<List<DefinedCheckNode>>();
          final var trueExpression = new AtomicReference<ExpressionNode>();
          final var falseExpression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .list(tests::set, DefinedCheckNode::parse, ',')
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
                new ExpressionNodeIfDefined(
                    p.line(),
                    p.column(),
                    tests.get(),
                    trueExpression.get(),
                    falseExpression.get()));
          }
          return result;
        });
    OUTER.addKeyword(
        "If",
        (p, o) -> {
          final var testExpression = new AtomicReference<ExpressionNode>();
          final var trueExpression = new AtomicReference<ExpressionNode>();
          final var falseExpression = new AtomicReference<ExpressionNode>();
          final var result =
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
          final var cases = new AtomicReference<List<Pair<ExpressionNode, ExpressionNode>>>();
          final var test = new AtomicReference<ExpressionNode>();
          final var alternative = new AtomicReference<ExpressionNode>();
          final var result =
              parse1(
                      parse(p.whitespace(), test::set)
                          .whitespace()
                          .list(
                              cases::set,
                              (cp, co) -> {
                                final var condition = new AtomicReference<ExpressionNode>();
                                final var value = new AtomicReference<ExpressionNode>();
                                final var cresult =
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
        "Match",
        (p, o) -> {
          final var cases = new AtomicReference<List<MatchBranchNode>>();
          final var test = new AtomicReference<ExpressionNode>();
          final var alternative = new AtomicReference<MatchAlternativeNode>();
          final var result =
              parse(p.whitespace(), test::set)
                  .whitespace()
                  .list(cases::set, MatchBranchNode::parse)
                  .whitespace()
                  .then(MatchAlternativeNode::parse, alternative::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ExpressionNodeMatch(
                    p.line(), p.column(), test.get(), cases.get(), alternative.get()));
          }
          return result;
        });
    OUTER.addKeyword(
        "For",
        (p, o) -> {
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var source = new AtomicReference<SourceNode>();
          final var transforms = new AtomicReference<List<ListNode>>();
          final var collector = new AtomicReference<CollectNode>();
          final var result =
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
          final var definitions =
              new AtomicReference<List<Pair<DestructuredArgumentNode, ExpressionNode>>>();
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .list(
                      definitions::set,
                      (defp, defo) -> {
                        final var name = new AtomicReference<DestructuredArgumentNode>();
                        final var expr = new AtomicReference<ExpressionNode>();
                        final var defResult =
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
    OUTER.addKeyword(
        "Tabulate",
        (p, o) -> {
          final var definitions =
              new AtomicReference<List<Pair<DestructuredArgumentNode, List<ExpressionNode>>>>();
          final var result =
              p.whitespace()
                  .list(
                      definitions::set,
                      (defp, defo) -> {
                        final var name = new AtomicReference<DestructuredArgumentNode>();
                        final var expr = new AtomicReference<List<ExpressionNode>>();
                        final var defResult =
                            defp.whitespace()
                                .then(DestructuredArgumentNode::parse, name::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .list(expr::set, ExpressionNode::parse, ',')
                                .whitespace()
                                .symbol(";")
                                .whitespace();
                        if (defResult.isGood()) {
                          defo.accept(new Pair<>(name.get(), expr.get()));
                        }
                        return defResult;
                      })
                  .whitespace()
                  .keyword("End")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new ExpressionNodeTabulate(p.line(), p.column(), definitions.get()));
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

    for (final var comparison : Comparison.values()) {
      COMPARISON.addSymbol(
          comparison.symbol(),
          (p, o) -> {
            final var right = new AtomicReference<ExpressionNode>();
            final var result = parse4(p.whitespace(), right::set).whitespace();
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
        "=~",
        (p, o) -> {
          final var regex = new AtomicReference<Pair<String, Integer>>();
          final var result =
              p.whitespace().regex(REGEX, regexParser(regex), "Regular expression.").whitespace();
          if (result.isGood()) {
            o.accept(
                left ->
                    new ExpressionNodeRegexBinding(
                        p.line(), p.column(), left, regex.get().first(), regex.get().second()));
          }
          return result;
        });
    COMPARISON.addSymbol(
        "~",
        (p, o) -> {
          final var regex = new AtomicReference<Pair<String, Integer>>();
          final var result =
              p.whitespace().regex(REGEX, regexParser(regex), "Regular expression.").whitespace();
          if (result.isGood()) {
            o.accept(
                left ->
                    new ExpressionNodeRegex(
                        p.line(), p.column(), left, regex.get().first(), regex.get().second()));
          }
          return result;
        });
    COMPARISON.addSymbol(
        "!~",
        (p, o) -> {
          final var regex = new AtomicReference<Pair<String, Integer>>();
          final var result =
              p.whitespace().regex(REGEX, regexParser(regex), "Regular expression.").whitespace();
          if (result.isGood()) {
            o.accept(
                left ->
                    new ExpressionNodeLogicalNot(
                        p.line(),
                        p.column(),
                        new ExpressionNodeRegex(
                            p.line(),
                            p.column(),
                            left,
                            regex.get().first(),
                            regex.get().second())));
          }
          return result;
        });
    DISJUNCTION.addSymbol(
        "+",
        binaryOperators(
            "+",
            BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.ADD),
            BinaryOperation.virtualMethod(
                Imyhat.DATE, Imyhat.INTEGER, Imyhat.DATE, "plusSeconds", "datePlusSeconds"),
            BinaryOperation.virtualMethod(
                Imyhat.PATH, Imyhat.PATH, Imyhat.PATH, "resolve", "pathResolve"),
            BinaryOperation::stringConcat,
            BinaryOperation.staticMethod(
                Imyhat.PATH,
                Imyhat.STRING,
                Imyhat.PATH,
                A_RUNTIME_SUPPORT_TYPE,
                "resolvePath",
                "pathResolve"),
            BinaryOperation.binaryListStaticMethod(A_RUNTIME_SUPPORT_TYPE, "union", "setUnion"),
            BinaryOperation.listAndItemStaticMethod(A_RUNTIME_SUPPORT_TYPE, "addItem", "setAdd"),
            BinaryOperation::tupleConcat,
            BinaryOperation::objectConcat));
    DISJUNCTION.addSymbol(
        "-",
        binaryOperators(
            "-",
            BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.SUB),
            BinaryOperation.virtualMethod(
                Imyhat.DATE, Imyhat.INTEGER, Imyhat.DATE, "minusSeconds", "dateMinusSeconds"),
            BinaryOperation.staticMethod(
                Imyhat.DATE,
                Imyhat.DATE,
                Imyhat.INTEGER,
                A_RUNTIME_SUPPORT_TYPE,
                "difference",
                "dateDifference"),
            BinaryOperation.binaryListStaticMethod(
                A_RUNTIME_SUPPORT_TYPE, "difference", "setDifference"),
            BinaryOperation.listAndItemStaticMethod(
                A_RUNTIME_SUPPORT_TYPE, "removeItem", "setRemove")));

    CONJUNCTION.addSymbol(
        "*",
        binaryOperators(
            "*",
            BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.MUL),
            BinaryOperation.exact(Imyhat.STRING, Imyhat.INTEGER, BinaryOperation.STRING_REPEAT)));
    CONJUNCTION.addSymbol(
        "/", binaryOperators("/", BinaryOperation.primitiveMathUpgrading(GeneratorAdapter.DIV)));
    CONJUNCTION.addSymbol(
        "%",
        binaryOperators("%", BinaryOperation.primitiveMath(Imyhat.INTEGER, GeneratorAdapter.REM)));

    SUFFIX_LOOSE.addKeyword(
        "In",
        (p, o) -> {
          final var collection = new AtomicReference<ExpressionNode>();
          final var result = parse7(p.whitespace(), collection::set);
          if (result.isGood()) {
            final var c = collection.get();
            o.accept(node -> new ExpressionNodeContains(p.line(), p.column(), node, c));
          }
          return result;
        });
    SUFFIX_LOOSE.addKeyword(
        "As",
        (p, o) -> {
          final var typeNode = new AtomicReference<ImyhatNode>();
          final var result = p.whitespace().then(ImyhatNode::parse, typeNode::set).whitespace();
          if (result.isGood()) {
            final var type = typeNode.get();
            o.accept(node -> new ExpressionNodeJsonConvert(p.line(), p.column(), node, type));
          }
          return result;
        });

    UNARY.addSymbol("!", just(ExpressionNodeLogicalNot::new));
    UNARY.addSymbol("-", just(ExpressionNodeNegate::new));
    UNARY.addKeyword("ConvertWdlPair", just(ExpressionNodeWdlPair::new));

    SUFFIX_TIGHT.addSymbol(
        "[",
        (p, o) -> {
          final var index = new AtomicLong();
          final var result =
              p.whitespace().integer(index::set, 10).whitespace().symbol("]").whitespace();
          if (result.isGood()) {
            final var i = (int) index.get();
            o.accept(node -> new ExpressionNodeTupleGet(p.line(), p.column(), node, i));
            return result;
          } else {
            final var indexExpression = new AtomicReference<ExpressionNode>();
            final var mapResult =
                p.whitespace()
                    .then(ExpressionNode::parse, indexExpression::set)
                    .whitespace()
                    .symbol("]")
                    .whitespace();
            if (mapResult.isGood()) {
              o.accept(
                  node ->
                      new ExpressionNodeDictionaryGet(
                          p.line(), p.column(), node, indexExpression.get()));
            }
            return mapResult;
          }
        });
    SUFFIX_TIGHT.addSymbol(
        ".",
        (p, o) -> {
          final var index = new AtomicReference<String>();
          final var result = p.whitespace().identifier(index::set).whitespace();
          if (result.isGood()) {
            o.accept(node -> new ExpressionNodeObjectGet(p.line(), p.column(), node, index.get()));
          }
          return result;
        });
    SUFFIX_TIGHT.addSymbol(
        "?",
        (p, o) -> {
          o.accept(node -> new ExpressionNodeOptionalUnbox(p.line(), p.column(), node));
          return p;
        });
    TERMINAL.addKeyword(
        "ActionName",
        (p, o) -> {
          o.accept(new ExpressionNodeActionName(p.line(), p.column()));
          return p.whitespace();
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
                    e ->
                        o.accept(
                            new ExpressionNodeDate(p.line(), p.column(), Instant.ofEpochMilli(e))),
                    10)
                .whitespace());
    TERMINAL.addKeyword(
        "EpochSecond",
        (p, o) ->
            p.whitespace()
                .integer(
                    e ->
                        o.accept(
                            new ExpressionNodeDate(p.line(), p.column(), Instant.ofEpochSecond(e))),
                    10)
                .whitespace());
    TERMINAL.addSymbol(
        "{",
        (p, o) ->
            parseTupleOrObject(
                p,
                o,
                (n) -> new ExpressionNodeGangTuple(p.line(), p.column(), n),
                f -> new ExpressionNodeObject(p.line(), p.column(), f),
                e -> new ExpressionNodeTuple(p.line(), p.column(), e)));
    TERMINAL.addKeyword(
        "Dict",
        (p, o) -> {
          final var fields = new AtomicReference<List<DictionaryElementNode>>();
          final var result =
              p.whitespace()
                  .symbol("{")
                  .list(fields::set, DictionaryElementNode::parse, ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new ExpressionNodeDictionary(p.line(), p.column(), fields.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "[",
        (p, o) -> {
          final var items = new AtomicReference<List<ExpressionNode>>();
          final var result =
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
          final var items = new AtomicReference<List<StringNode>>();
          final var result = p.list(items::set, StringNode::parse).symbol("\"").whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeString(p.line(), p.column(), items.get()));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "'",
        (p, o) -> {
          final var path = new AtomicReference<List<String>>();
          final var result =
              p.list(path::set, (ip, io) -> ip.dispatch(PATH, io)).symbol("'").whitespace();
          if (p.isGood()) {
            o.accept(
                new ExpressionNodePathLiteral(p.line(), p.column(), String.join("", path.get())));
          }
          return result;
        });
    TERMINAL.addSymbol(
        "`",
        (p, o) -> {
          final var value = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, value::set)
                  .whitespace()
                  .symbol("`")
                  .whitespace();
          if (p.isGood()) {
            o.accept(new ExpressionNodeOptionalOf(p.line(), p.column(), value.get()));
          }
          final var emptyResult = p.whitespace().symbol("`").whitespace();
          if (emptyResult.isGood()) {
            o.accept(new ExpressionNodeOptionalEmpty(p.line(), p.column()));
            return emptyResult;
          }
          return result;
        });
    TERMINAL.addSymbol(
        "(",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              parse(p.whitespace(), expression::set).whitespace().symbol(")").whitespace();
          if (result.isGood()) {
            o.accept(expression.get());
          }
          return result;
        });
    TERMINAL.addRaw(
        "floating-point number",
        (p, o) -> {
          final var value = new AtomicReference<Double>();
          final var result =
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
          final var value = new AtomicLong();
          final var multiplier = new AtomicInteger();
          final var result =
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
          return p.whitespace();
        });
    TERMINAL.addKeyword(
        "False",
        (p, o) -> {
          o.accept(new ExpressionNodeBoolean(p.line(), p.column(), false));
          return p.whitespace();
        });
    TERMINAL.addKeyword(
        "Location",
        (p, o) -> {
          o.accept(new ExpressionNodeLocation(p.line(), p.column()));
          return p.whitespace();
        });
    TERMINAL.addRaw(
        "function call, variable, algebraic value",
        (p, o) -> {
          final var name = new AtomicReference<String>();
          final var algebraicResult = p.algebraicIdentifier(name::set).whitespace();
          if (algebraicResult.isGood()) {
            if (algebraicResult.lookAhead('{')) {
              return parseTupleOrObject(
                  algebraicResult.symbol("{"),
                  o,
                  (n) -> new ExpressionNodeAlgebraicGangTuple(p.line(), p.column(), name.get(), n),
                  f -> new ExpressionNodeAlgebraicObject(p.line(), p.column(), name.get(), f),
                  e -> new ExpressionNodeAlgebraicTuple(p.line(), p.column(), name.get(), e));
            } else {
              o.accept(
                  new ExpressionNodeAlgebraicTuple(p.line(), p.column(), name.get(), List.of()));
            }
            return algebraicResult;
          }

          var result = p.qualifiedIdentifier(name::set);
          if (result.isGood()) {
            if (result.lookAhead('(')) {
              final var items = new AtomicReference<List<ExpressionNode>>();
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

    PATH.addSymbol("\\'", Parser.just("'"));
    PATH.addSymbol("\\\\", Parser.just("\\"));
    PATH.addSymbol("\\n", Parser.just("\n"));
    PATH.addRaw(
        "valid characters",
        (p, o) ->
            p.regex(PATH_CHUNK, m -> o.accept(m.group(0)), "Valid path characters required."));
  }

  private static Parser parseTupleOrObject(
      Parser parser,
      Consumer<ExpressionNode> output,
      Function<String, ExpressionNode> gangConstructor,
      Function<List<ObjectElementNode>, ExpressionNode> objectContsructor,
      Function<List<TupleElementNode>, ExpressionNode> tupleConstructor) {
    final var gangParser = parser.whitespace().symbol("@");
    if (gangParser.isGood()) {
      final var name = new AtomicReference<String>();
      final var gangResult =
          gangParser.whitespace().identifier(name::set).whitespace().symbol("}").whitespace();
      if (gangResult.isGood()) {
        output.accept(gangConstructor.apply(name.get()));
      }
      return gangResult;
    }
    final var fields = new AtomicReference<List<ObjectElementNode>>();
    var objectResult =
        parser.whitespace().listEmpty(fields::set, ObjectElementNode::parse, ',').whitespace();
    if (objectResult.symbol(";").isGood()) {
      objectResult =
          objectResult
              .symbol(";")
              .<ObjectElementNode>list(
                  fields.get()::addAll,
                  (cfp, cfo) -> {
                    final var cgp = cfp.whitespace().symbol("@");
                    if (cgp.isGood()) {
                      return cgp.whitespace()
                          .identifier(
                              n ->
                                  cfo.accept(
                                      new ObjectElementNodeGang(cfp.line(), cfp.column(), n)))
                          .whitespace();
                    }
                    return cfp.whitespace()
                        .identifier(
                            n ->
                                cfo.accept(
                                    new ObjectElementNodeSingle(
                                        n,
                                        new ExpressionNodeVariable(cfp.line(), cfp.column(), n))))
                        .whitespace();
                  },
                  ',')
              .whitespace();
    }
    objectResult = objectResult.symbol("}").whitespace();
    if (objectResult.isGood() && !fields.get().isEmpty()) {
      output.accept(objectContsructor.apply(fields.get()));
      return objectResult;
    }

    final var items = new AtomicReference<List<TupleElementNode>>();
    final var result =
        parser
            .whitespace()
            .list(items::set, TupleElementNode::parse, ',')
            .whitespace()
            .symbol("}")
            .whitespace();
    if (parser.isGood()) {
      output.accept(tupleConstructor.apply(items.get()));
    }
    return result;
  }

  public static Consumer<Matcher> regexParser(AtomicReference<Pair<String, Integer>> regex) {
    return m -> {
      var flags = 0;

      if (m.group(2).contains("i")) {
        flags |= Pattern.CASE_INSENSITIVE;
      }
      if (m.group(2).contains("m")) {
        flags |= Pattern.MULTILINE;
      }
      if (m.group(2).contains("s")) {
        flags |= Pattern.DOTALL;
      }
      if (m.group(2).contains("u")) {
        flags |= Pattern.UNICODE_CASE;
      }
      regex.set(new Pair<>(m.group(1), flags));
    };
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

  public final int column() {
    return column;
  }

  /** Provides a name that can be used for dump columns, if possible */
  public Optional<String> dumpColumnName() {
    return Optional.empty();
  }

  public final int line() {
    return line;
  }

  /** Produce ES6/JavaScript code for this expression */
  public abstract String renderEcma(EcmaScriptRenderer renderer);

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
  protected final void typeError(Imyhat acceptable, Imyhat found, Consumer<String> errorHandler) {
    generateTypeError(line(), column(), "", acceptable, found, errorHandler);
  }

  /**
   * Convenience function to produce a type error
   *
   * @param acceptable the allowed type
   * @param found the type provided
   */
  protected final void typeError(String acceptable, Imyhat found, Consumer<String> errorHandler) {
    errorHandler.accept(
        String.format(
            "%d:%d: Expected %s, but got %s.", line(), column(), acceptable, found.name()));
  }
}
