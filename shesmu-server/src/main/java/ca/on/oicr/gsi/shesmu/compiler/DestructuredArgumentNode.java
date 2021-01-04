package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DestructuredArgumentNode implements UndefinedVariableProvider {

  private static final Parser.ParseDispatch<DestructuredArgumentNode> DISPATCH =
      new Parser.ParseDispatch<>();
  private static final DestructuredArgumentNode MISSING =
      new DestructuredArgumentNode() {
        @Override
        public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
          // Pretend like we are read because we don't want to generate errors that something was
          // unread
          return true;
        }

        @Override
        public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
          return WildcardCheck.NONE;
        }

        @Override
        public boolean isBlank() {
          return true;
        }

        @Override
        public Stream<LoadableValue> render(Consumer<Renderer> rendererConsumer) {
          return Stream.empty();
        }

        @Override
        public Stream<EcmaLoadableValue> renderEcma(Function<EcmaScriptRenderer, String> loader) {
          return Stream.empty();
        }

        @Override
        public boolean resolve(
            ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
          return true;
        }

        @Override
        public void setFlavour(Target.Flavour flavour) {
          // Do nothing
        }

        @Override
        public Stream<DefinedTarget> targets() {
          return Stream.empty();
        }

        @Override
        public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
          return true;
        }
      };

  public static <T> Parser parseTupleOrObject(
      Parser p,
      Consumer<T> o,
      Function<List<Pair<String, DestructuredArgumentNode>>, T> objectConstructor,
      Function<List<DestructuredArgumentNode>, T> tupleConstructor) {
    {
      final AtomicReference<List<Pair<String, DestructuredArgumentNode>>> inner =
          new AtomicReference<>();
      Parser result =
          p.whitespace()
              .listEmpty(inner::set, (ip, io) -> parseInner(ip, io, true), ',')
              .whitespace();
      if (result.symbol(";").isGood()) {
        result =
            result
                .symbol(";")
                .<Pair<String, DestructuredArgumentNode>>list(
                    f -> inner.get().addAll(f),
                    (cfp, cfo) ->
                        cfp.whitespace()
                            .identifier(
                                n ->
                                    cfo.accept(
                                        new Pair<>(
                                            n,
                                            new DestructuredArgumentNodeVariable(
                                                cfp.line(), cfp.column(), n))))
                            .whitespace(),
                    ',')
                .whitespace();
      }

      result = result.symbol("}").whitespace();
      if (result.isGood() && !inner.get().isEmpty()) {
        final Map<Boolean, Long> formats =
            inner
                .get()
                .stream()
                .collect(Collectors.partitioningBy(x -> x.first() == null, Collectors.counting()));
        if ((formats.get(true) > 0) && (formats.get(false) > 0)) {
          return result.raise("Destructuring is a mixture of object and tuple fields.");
        }
        if (formats.get(true) == 0) {
          o.accept(objectConstructor.apply(inner.get()));
        } else {
          o.accept(
              tupleConstructor.apply(
                  inner.get().stream().map(Pair::second).collect(Collectors.toList())));
        }
      }
      return result;
    }
  }

  static {
    DISPATCH.addSymbol(
        "{",
        (p, o) ->
            parseTupleOrObject(
                p,
                o,
                f -> new DestructuredArgumentNodeObject(p.line(), p.column(), f),
                f -> new DestructuredArgumentNodeTuple(p.line(), p.column(), f)));
    DISPATCH.addKeyword(
        "_",
        (p, o) -> {
          o.accept(MISSING);
          return p.whitespace();
        });
    DISPATCH.addKeyword(
        "*",
        (p, o) -> {
          o.accept(new DestructuredArgumentNodeStar(p.line(), p.column()));
          return p.whitespace();
        });
    DISPATCH.addRaw(
        "variable",
        (p, o) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final Parser result = p.identifier(name::set).whitespace();
          if (result.isGood()) {
            final Parser asResult = result.keyword("As");
            if (asResult.isGood()) {
              final AtomicReference<ImyhatNode> type = new AtomicReference<>();
              final Parser converted =
                  asResult.whitespace().then(ImyhatNode::parse, type::set).whitespace();
              if (converted.isGood()) {
                o.accept(
                    new DestructuredArgumentNodeConvertedVariable(
                        p.line(), p.column(), name.get(), type.get()));
              }
              return converted;
            }
            o.accept(new DestructuredArgumentNodeVariable(p.line(), p.column(), name.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<DestructuredArgumentNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output);
  }

  public static Parser parseInner(
      Parser parser,
      Consumer<Pair<String, DestructuredArgumentNode>> output,
      boolean tupleAllowed) {
    final AtomicReference<DestructuredArgumentNode> node = new AtomicReference<>();
    final AtomicReference<String> name = new AtomicReference<>();
    final Parser childResult = parse(parser, node::set);
    if (childResult.isGood()) {
      final Parser objectParser =
          childResult.whitespace().symbol("=").whitespace().identifier(name::set).whitespace();
      if (objectParser.isGood()) {
        output.accept(new Pair<>(name.get(), node.get()));
        return objectParser;
      } else if (tupleAllowed) {
        output.accept(new Pair<>(null, node.get()));
        return childResult;
      } else {
        return parser;
      }
    } else {
      return childResult;
    }
  }

  public abstract boolean checkUnusedDeclarations(Consumer<String> errorHandler);

  public abstract WildcardCheck checkWildcard(Consumer<String> errorHandler);

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return Optional.empty();
  }

  public abstract boolean isBlank();

  public abstract Stream<LoadableValue> render(Consumer<Renderer> loader);

  public abstract Stream<EcmaLoadableValue> renderEcma(Function<EcmaScriptRenderer, String> loader);

  public abstract boolean resolve(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract void setFlavour(Target.Flavour flavour);

  public abstract Stream<DefinedTarget> targets();

  public abstract boolean typeCheck(Imyhat type, Consumer<String> errorHandler);
}
