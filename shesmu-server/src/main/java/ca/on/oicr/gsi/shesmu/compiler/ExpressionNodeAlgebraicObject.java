package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeAlgebraicObject extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_ALGEBRAIC_TYPE = Type.getType(AlgebraicValue.class);

  private static final Method CTOR_ALGEBRAIC =
      new Method(
          "<init>",
          Type.VOID_TYPE,
          new Type[] {Type.getType(String.class), Type.getType(Object[].class)});

  private final List<ObjectElementNode> fields;
  private final String name;

  private Imyhat type;

  public ExpressionNodeAlgebraicObject(
      int line, int column, String name, List<ObjectElementNode> fields) {
    super(line, column);
    this.name = name;
    this.fields = fields;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    fields.forEach(field -> field.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    fields.forEach(field -> field.collectPlugins(pluginFileNames));
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {

    return String.format(
        "{\"type\": \"%s\", \"contents\": %s}",
        name,
        fields.stream()
            .flatMap(f -> f.render(renderer))
            .sorted()
            .collect(Collectors.joining(", ", "{", "}")));
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());
    final Map<String, Integer> indices =
        fields.stream()
            .flatMap(ObjectElementNode::names)
            .map(Pair::first)
            .sorted()
            .map(Pair.number())
            .collect(Collectors.toMap(Pair::second, Pair::first));

    renderer.methodGen().newInstance(A_ALGEBRAIC_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(name);
    renderer.methodGen().push(indices.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (final ObjectElementNode element : fields) {
      element.render(renderer, indices::get);
    }

    renderer.mark(line());

    renderer.methodGen().invokeConstructor(A_ALGEBRAIC_TYPE, CTOR_ALGEBRAIC);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return fields.stream().filter(field -> field.resolve(defs, errorHandler)).count()
        == fields.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return fields.stream()
            .filter(field -> field.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == fields.size();
  }

  @Override
  public Imyhat type() {
    return type == null ? Imyhat.BAD : type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        fields.stream().filter(field -> field.typeCheck(errorHandler)).count() == fields.size();
    final Map<String, Long> fieldCounts =
        fields.stream()
            .flatMap(ObjectElementNode::names)
            .map(Pair::first)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (fieldCounts.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .peek(
                e ->
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Field %s is defined %d times.",
                            line(), column(), e.getKey(), e.getValue())))
            .count()
        > 0) {
      ok = false;
    }
    if (ok) {
      type = Imyhat.algebraicObject(name, fields.stream().flatMap(ObjectElementNode::names));
    }
    return ok;
  }
}
