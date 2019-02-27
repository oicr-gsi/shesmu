package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeObject extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

  private static final Method CTOR_TUPLE =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});

  private final List<Pair<String, ExpressionNode>> fields;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeObject(int line, int column, List<Pair<String, ExpressionNode>> fields) {
    super(line, column);
    this.fields = fields;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    fields.forEach(field -> field.second().collectFreeVariables(names, predicate));
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

    renderer.methodGen().newInstance(A_TUPLE_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(fields.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    final AtomicInteger index = new AtomicInteger();
    fields
        .stream()
        .sorted(Comparator.comparing(Pair::first))
        .forEach(
            field -> {
              renderer.methodGen().dup();
              renderer.methodGen().push(index.getAndIncrement());
              field.second().render(renderer);
              renderer.methodGen().valueOf(field.second().type().apply(TypeUtils.TO_ASM));
              renderer.methodGen().arrayStore(A_OBJECT_TYPE);
            });
    renderer.mark(line());

    renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return fields.stream().filter(field -> field.second().resolve(defs, errorHandler)).count()
        == fields.size();
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return fields
            .stream()
            .filter(field -> field.second().resolveFunctions(definedFunctions, errorHandler))
            .count()
        == fields.size();
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        fields.stream().filter(field -> field.second().typeCheck(errorHandler)).count()
            == fields.size();
    final Map<String, Long> fieldCounts =
        fields
            .stream()
            .map(Pair::first)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (fieldCounts
            .entrySet()
            .stream()
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
      type =
          new Imyhat.ObjectImyhat(
              fields.stream().map(field -> new Pair<>(field.first(), field.second().type())));
    }
    return ok;
  }
}
