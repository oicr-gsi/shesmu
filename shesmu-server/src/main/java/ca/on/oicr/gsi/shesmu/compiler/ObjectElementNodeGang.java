package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public final class ObjectElementNodeGang extends ObjectElementNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private final int column;
  private GangDefinition definition;
  private List<Pair<Target, Imyhat>> elements;
  private final String gang;
  private final int line;

  public ObjectElementNodeGang(int line, int column, String gang) {
    this.line = line;
    this.column = column;
    this.gang = gang;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final var element : elements) {
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
  public Stream<Pair<String, Imyhat>> names() {
    return elements.stream().map(p -> new Pair<>(p.first().name(), p.second()));
  }

  @Override
  public Stream<String> render(EcmaScriptRenderer renderer) {
    return elements.stream()
        .map(
            element ->
                String.format("%s: %s", element.first().name(), renderer.load(element.first())));
  }

  @Override
  public void render(Renderer renderer, ToIntFunction<String> indexOf) {
    elements.stream()
        .forEach(
            element -> {
              renderer.methodGen().dup();
              renderer.methodGen().push(indexOf.applyAsInt(element.first().name()));
              renderer.loadTarget(element.first());
              renderer.methodGen().valueOf(element.second().apply(TO_ASM));
              renderer.methodGen().arrayStore(A_OBJECT_TYPE);
            });
  }

  @Override
  public Stream<String> renderConstant(EcmaScriptRenderer renderer) {
    return elements.stream()
        .map(
            element ->
                String.format(
                    "%s: { type: \"%s\", value: %s}",
                    element.first().name(),
                    element.second().descriptor(),
                    renderer.load(element.first())));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final var result =
        TypeUtils.matchGang(
            line,
            column,
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
    final var groupDefinition =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(gang))
            .findAny();
    if (groupDefinition.isPresent()) {
      definition = groupDefinition.get();
      return true;
    }
    errorHandler.accept(String.format("%d:%d: Unknown gang “%s”.", line, column, gang));
    return false;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return elements.stream()
            .filter(
                p -> {
                  if (p.first().type().isSame(p.second())) {
                    return true;
                  }
                  errorHandler.accept(
                      String.format(
                          "%d:%d: Gang variable %s should have type %s but got %s.",
                          line,
                          column,
                          p.first().name(),
                          p.second().name(),
                          p.first().type().name()));
                  return false;
                })
            .count()
        == elements.size();
  }
}
