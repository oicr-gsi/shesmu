package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DestructuredArgumentNode {
  public static Parser parse(Parser parser, Consumer<DestructuredArgumentNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output);
  }

  private static Parser parseInner(Parser ip, Consumer<Pair<String, DestructuredArgumentNode>> io) {
    final AtomicReference<DestructuredArgumentNode> node = new AtomicReference<>();
    final AtomicReference<String> name = new AtomicReference<>();
    final Parser childResult = parse(ip, node::set);
    if (childResult.isGood()) {
      final Parser objectParser =
          childResult.whitespace().symbol("=").whitespace().identifier(name::set).whitespace();
      if (objectParser.isGood()) {
        io.accept(new Pair<>(name.get(), node.get()));
        return objectParser;
      } else {
        io.accept(new Pair<>(null, node.get()));
        return childResult;
      }
    } else {
      return childResult;
    }
  }

  private static final Parser.ParseDispatch<DestructuredArgumentNode> DISPATCH =
      new Parser.ParseDispatch<>();
  private static final DestructuredArgumentNode MISSING =
      new DestructuredArgumentNode() {
        @Override
        public boolean isBlank() {
          return true;
        }

        @Override
        public Stream<LoadableValue> render(Consumer<Renderer> rendererConsumer) {
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
        public Stream<Target> targets() {
          return Stream.empty();
        }

        @Override
        public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
          return true;
        }
      };

  static {
    DISPATCH.addSymbol(
        "{",
        (p, o) -> {
          final AtomicReference<List<Pair<String, DestructuredArgumentNode>>> inner =
              new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .list(inner::set, DestructuredArgumentNode::parseInner, ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();
          if (result.isGood()) {
            final Map<Boolean, Long> formats =
                inner
                    .get()
                    .stream()
                    .collect(
                        Collectors.partitioningBy(x -> x.first() == null, Collectors.counting()));
            if ((formats.get(true) > 0) && (formats.get(false) > 0)) {
              return result.raise("Destructuring is a mixture of object and tuple fields.");
            }
            if (formats.get(true) == 0) {
              o.accept(new DestructuredArgumentNodeObject(p.line(), p.column(), inner.get()));
            } else {
              o.accept(
                  new DestructuredArgumentNodeTuple(
                      p.line(),
                      p.column(),
                      inner.get().stream().map(Pair::second).collect(Collectors.toList())));
            }
          }
          return result;
        });
    DISPATCH.addKeyword(
        "_",
        (p, o) -> {
          o.accept(MISSING);
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
            o.accept(new DestructuredArgumentNodeVariable(name.get()));
          }
          return result;
        });
  }

  public abstract boolean isBlank();

  public abstract Stream<LoadableValue> render(Consumer<Renderer> loader);

  public abstract boolean resolve(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract void setFlavour(Target.Flavour flavour);

  public abstract Stream<Target> targets();

  public abstract boolean typeCheck(Imyhat type, Consumer<String> errorHandler);
}
