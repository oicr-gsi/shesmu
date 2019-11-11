package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeGangTuple extends ExpressionNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method TUPLE__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private GangDefinition definition;
  private List<Pair<Target, Imyhat>> elements;
  private final String name;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeGangTuple(int line, int column, String name) {
    super(line, column);
    this.name = name;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final Pair<Target, Imyhat> element : elements) {
      if (predicate.test(element.first().flavour())) {
        names.add(element.first().name());
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().newInstance(A_TUPLE_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(elements.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (int i = 0; i < elements.size(); i++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(i);
      renderer.loadTarget(elements.get(i).first());
      renderer.methodGen().valueOf(elements.get(i).first().type().apply(TO_ASM));
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, TUPLE__CTOR);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<List<Pair<Target, Imyhat>>> result =
        TypeUtils.matchGang(
            line(),
            column(),
            defs,
            definition,
            (target, expectedType, dropIfDefault) -> new Pair<>(target, expectedType),
            errorHandler);
    result.ifPresent(x -> elements = x);
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Optional<? extends GangDefinition> groupDefinition =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(name))
            .findAny();
    if (groupDefinition.isPresent()) {
      definition = groupDefinition.get();
      return true;
    }
    errorHandler.accept(String.format("%d:%d: Unknown gang “%s”.", line(), column(), name));
    return false;
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    type = Imyhat.tuple(elements.stream().map(e -> e.first().type()).toArray(Imyhat[]::new));
    return elements
            .stream()
            .filter(
                p -> {
                  if (p.first().type().isSame(p.second())) {
                    return true;
                  }
                  errorHandler.accept(
                      String.format(
                          "%d:%d: Gang variable %s should have type %s but got %s.",
                          line(),
                          column(),
                          p.first().name(),
                          p.second().name(),
                          p.first().type().name()));
                  return false;
                })
            .count()
        == elements.size();
  }
}
