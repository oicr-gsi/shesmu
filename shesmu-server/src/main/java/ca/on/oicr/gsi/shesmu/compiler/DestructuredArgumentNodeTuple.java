package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DestructuredArgumentNodeTuple extends DestructuredArgumentNode {
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__GET =
      new Method("get", Type.getType(Object.class), new Type[] {Type.INT_TYPE});
  private final int column;
  private final List<DestructuredArgumentNode> elements;
  private final int line;
  private Imyhat.TupleImyhat tupleType;

  public DestructuredArgumentNodeTuple(
      int line, int column, List<DestructuredArgumentNode> elements) {
    this.column = column;
    this.line = line;
    this.elements = elements;
  }

  @Override
  public boolean isBlank() {
    return elements.stream().allMatch(DestructuredArgumentNode::isBlank);
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final DestructuredArgumentNode element : elements) {
      if (!element.checkUnusedDeclarations(errorHandler)) {
        ok = false;
      }
    }
    return ok;
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    final WildcardCheck result =
        elements.stream()
            .map(element -> element.checkWildcard(errorHandler))
            .reduce(WildcardCheck.NONE, WildcardCheck::combine);
    if (result == WildcardCheck.BAD) {
      errorHandler.accept(
          String.format("%d:%d: Multiple wildcards are not allowed in tuple.", line, column));
    }
    return result;
  }

  @Override
  public Stream<LoadableValue> render(Consumer<Renderer> loader) {
    return IntStream.range(0, elements.size())
        .boxed()
        .flatMap(
            i ->
                elements
                    .get(i)
                    .render(
                        r -> {
                          loader.accept(r);
                          r.methodGen().push(i);
                          r.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
                          r.methodGen().unbox(tupleType.get(i).apply(TO_ASM));
                        }));
  }

  @Override
  public Stream<EcmaLoadableValue> renderEcma(String loader) {
    return IntStream.range(0, elements.size())
        .boxed()
        .flatMap(i -> elements.get(i).renderEcma(loader + "[" + i + "]"));
  }

  @Override
  public boolean resolve(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return elements.stream()
            .filter(element -> element.resolve(expressionCompilerServices, errorHandler))
            .count()
        == elements.size();
  }

  @Override
  public void setFlavour(Target.Flavour flavour) {
    for (final DestructuredArgumentNode element : elements) {
      element.setFlavour(flavour);
    }
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return elements.stream().flatMap(DestructuredArgumentNode::targets);
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    if (type instanceof Imyhat.TupleImyhat) {
      tupleType = (Imyhat.TupleImyhat) type;
      if (tupleType.count() != elements.size()) {
        errorHandler.accept(
            String.format(
                "%d:%d: Tuple has %d elements, but destructuring expects %d.",
                line, column, tupleType.count(), elements.size()));
        return false;
      }
      return IntStream.range(0, elements.size())
          .allMatch(i -> elements.get(i).typeCheck(tupleType.get(i), errorHandler));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Tuple expected for destructuring, but got %s.", line, column, type.name()));
      return false;
    }
  }
}
