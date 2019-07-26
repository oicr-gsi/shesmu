package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DestructuredArgumentNodeObject extends DestructuredArgumentNode {
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__GET =
      new Method("get", Type.getType(Object.class), new Type[] {Type.INT_TYPE});
  private final int column;
  private final List<Pair<String, DestructuredArgumentNode>> fields;
  private final int line;
  private Imyhat.ObjectImyhat objectType;

  public DestructuredArgumentNodeObject(
      int line, int column, List<Pair<String, DestructuredArgumentNode>> fields) {
    this.line = line;
    this.column = column;
    this.fields = fields;
  }

  @Override
  public boolean isBlank() {
    return fields.stream().allMatch(f -> f.second().isBlank());
  }

  @Override
  public Stream<LoadableValue> render(Consumer<Renderer> loader) {
    return IntStream.range(0, fields.size())
        .boxed()
        .flatMap(
            i ->
                fields
                    .get(i)
                    .second()
                    .render(
                        r -> {
                          loader.accept(r);
                          r.methodGen().push(objectType.index(fields.get(i).first()));
                          r.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
                          r.methodGen().unbox(objectType.get(fields.get(i).first()).apply(TO_ASM));
                        }));
  }

  @Override
  public void setFlavour(Target.Flavour flavour) {
    for (final Pair<String, DestructuredArgumentNode> field : fields) {
      field.second().setFlavour(flavour);
    }
  }

  @Override
  public Stream<Target> targets() {
    return fields.stream().flatMap(f -> f.second().targets());
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    final Map<String, Long> elementCounts =
        fields
            .stream()
            .map(Pair::first)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (elementCounts
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .peek(
                e ->
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Multiple use of object field %s in destructuring.",
                            line, column, e.getKey())))
            .count()
        > 0) {
      return false;
    }
    if (type instanceof Imyhat.ObjectImyhat) {
      objectType = (Imyhat.ObjectImyhat) type;
      boolean ok = true;
      for (final Pair<String, DestructuredArgumentNode> field : fields) {
        final Imyhat fieldType = objectType.get(field.first());
        if (fieldType.isBad()) {
          ok = false;
          errorHandler.accept(
              String.format(
                  "%d:%d: Field %s does not exist in object %s.",
                  line, column, field.first(), objectType.name()));
        } else {
          ok &= field.second().typeCheck(fieldType, errorHandler);
        }
      }
      return ok;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Object expected for destructuring, but got %s.", line, column, type.name()));
      return false;
    }
  }
}
