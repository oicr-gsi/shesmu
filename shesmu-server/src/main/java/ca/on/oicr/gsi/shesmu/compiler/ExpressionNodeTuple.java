package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeTuple extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

  private static final Method CTOR_TUPLE =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});

  private final List<TupleElementNode> items;

  private Imyhat.TupleImyhat type;

  public ExpressionNodeTuple(int line, int column, List<TupleElementNode> items) {
    super(line, column);
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
    return items.stream().map(e -> e.render(renderer)).collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

    renderer.methodGen().newInstance(A_TUPLE_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(type.count());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    var index = 0;
    for (final var element : items) {
      index = element.render(renderer, index);
    }
    renderer.mark(line());

    renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return items.stream()
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
    final var ok =
        items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
    if (ok) {
      type = Imyhat.tuple(items.stream().flatMap(TupleElementNode::types).toArray(Imyhat[]::new));
    }
    return ok;
  }
}
