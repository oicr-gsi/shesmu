package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.TupleImyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ExpressionNodeWdlPair extends ExpressionNode {

  private final ExpressionNode inner;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeWdlPair(int line, int column, ExpressionNode inner) {
    super(line, column);
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    inner.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    inner.collectPlugins(pluginFileNames);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final String value = inner.renderEcma(renderer);
    if (type instanceof TupleImyhat) {
      return String.format("[ %1$s.left, %1$s.right ]", value);
    } else {
      return String.format("{left: %1$s[0], right: %1$s[1]}", value);

    }
  }

  @Override
  public void render(Renderer renderer) {
    inner.render(renderer);

    // Whether in the object or tuple form, Shemu's internal representation is a Tuple object and
    // since the fields are in the correct order, there is no need to do anything with the object.
    // This is zero cost.
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return inner.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (inner.typeCheck(errorHandler)) {
      if (inner.type() instanceof TupleImyhat) {
        final TupleImyhat tuple = (TupleImyhat) inner.type();
        if (tuple.count() == 2) {
          type =
              new ObjectImyhat(
                  Stream.of(new Pair<>("left", tuple.get(0)), new Pair<>("right", tuple.get(1))));
          return true;
        }
        typeError("tuple of two items", inner.type(), errorHandler);
      } else if (inner.type() instanceof ObjectImyhat) {
        final ObjectImyhat object = (ObjectImyhat) inner.type();
        final long count = object.fields().count();
        final Optional<Imyhat> left =
            object
                .fields()
                .filter(e -> e.getKey().equals("left"))
                .map(e -> e.getValue().first())
                .findFirst();
        final Optional<Imyhat> right =
            object
                .fields()
                .filter(e -> e.getKey().equals("right"))
                .map(e -> e.getValue().first())
                .findFirst();
        if (count == 2 && left.isPresent() && right.isPresent()) {
          type = Imyhat.tuple(left.get(), right.get());
          return true;
        }
        typeError("object with only left and right", inner.type(), errorHandler);
      } else {
        typeError(
            "tuple of two items or object with only left and right", inner.type(), errorHandler);
      }
    }
    return false;
  }
}
