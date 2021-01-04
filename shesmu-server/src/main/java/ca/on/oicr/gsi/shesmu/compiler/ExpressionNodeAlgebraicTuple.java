package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeAlgebraicTuple extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_ALGEBRAIC_TYPE = Type.getType(AlgebraicValue.class);

  private static final Method CTOR_ALGEBRAIC =
      new Method(
          "<init>",
          Type.VOID_TYPE,
          new Type[] {Type.getType(String.class), Type.getType(Object[].class)});

  private final String name;
  private final List<TupleElementNode> items;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeAlgebraicTuple(
      int line, int column, String name, List<TupleElementNode> items) {
    super(line, column);
    this.name = name;
    this.items = items;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    items.forEach(item -> item.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    items.forEach(item -> item.collectPlugins(pluginFileNames));
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{\"type\": \"%s\", \"contents\": %s}",
        name,
        items.stream().map(e -> e.render(renderer)).collect(Collectors.joining(", ", "[", "]")));
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

    renderer.methodGen().newInstance(A_ALGEBRAIC_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(name);
    renderer.methodGen().push((int) items.stream().flatMap(TupleElementNode::types).count());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    int index = 0;
    for (final TupleElementNode element : items) {
      index = element.render(renderer, index);
    }
    renderer.mark(line());

    renderer.methodGen().invokeConstructor(A_ALGEBRAIC_TYPE, CTOR_ALGEBRAIC);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return items
            .stream()
            .filter(item -> item.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == items.size();
  }

  @Override
  public Imyhat type() {
    return type == null ? Imyhat.BAD : type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final boolean ok =
        items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
    if (ok) {
      type =
          Imyhat.algebraicTuple(
              name, items.stream().flatMap(TupleElementNode::types).toArray(Imyhat[]::new));
    }
    return ok;
  }
}
