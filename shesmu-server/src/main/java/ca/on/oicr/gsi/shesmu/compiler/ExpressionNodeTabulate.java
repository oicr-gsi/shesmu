package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeTabulate extends ExpressionNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TREE_MAP_TYPE = Type.getType(TreeMap.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[0]);
  private static final Method METHOD_TREE_MAP__PUT =
      new Method("put", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  public static final Method TUPLE__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});

  private final List<Pair<DestructuredArgumentNode, List<ExpressionNode>>> contents;

  public ExpressionNodeTabulate(
      int line, int column, List<Pair<DestructuredArgumentNode, List<ExpressionNode>>> contents) {
    super(line, column);
    this.contents = contents;
    for (final var content : contents) {
      content.first().setFlavour(Target.Flavour.LAMBDA);
    }
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final var content : contents) {
      for (final var expression : content.second()) {
        expression.collectFreeVariables(names, predicate);
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final var content : contents) {
      for (final var expression : content.second()) {
        expression.collectPlugins(pluginFileNames);
      }
    }
  }

  @Override
  public void render(Renderer renderer) {
    final var locals = new TreeMap<String, Integer>();
    for (final var content : contents) {
      content
          .first()
          .targets()
          .forEach(
              target -> {
                final var local = renderer.methodGen().newLocal(A_TREE_MAP_TYPE);
                locals.put(target.name(), local);
                renderer.methodGen().newInstance(A_TREE_MAP_TYPE);
                renderer.methodGen().dup();
                renderer.methodGen().invokeConstructor(A_TREE_MAP_TYPE, DEFAULT_CTOR);
                renderer.methodGen().storeLocal(local);
              });
      final var expressionLocal =
          renderer.methodGen().newLocal(content.second().get(0).type().apply(TO_ASM));
      for (var i = 0; i < content.second().size(); i++) {
        final var index = Integer.toString(i);

        content.second().get(i).render(renderer);
        renderer.methodGen().storeLocal(expressionLocal);

        content
            .first()
            .render(r -> r.methodGen().loadLocal(expressionLocal))
            .forEach(
                v -> {
                  renderer.methodGen().loadLocal(locals.get(v.name()));
                  renderer.methodGen().push(index);
                  v.accept(renderer);
                  renderer.methodGen().valueOf(v.type());
                  renderer.methodGen().invokeVirtual(A_TREE_MAP_TYPE, METHOD_TREE_MAP__PUT);
                  renderer.methodGen().pop();
                });
      }
    }

    renderer.methodGen().newInstance(A_TUPLE_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().push(locals.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    var index = 0;
    for (final var local : locals.values()) {
      renderer.methodGen().dup();
      renderer.methodGen().push(index++);
      renderer.methodGen().loadLocal(local);
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, TUPLE__CTOR);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return contents.stream()
        .flatMap(
            content ->
                IntStream.range(0, content.second().size())
                    .mapToObj(
                        i -> {
                          final var index = String.format("\"%d\": ", i);
                          return content
                              .first()
                              .renderEcma(
                                  renderer.newConst(content.second().get(i).renderEcma(renderer)))
                              .map(v -> new Pair<>(v.name(), index + v.get()));
                        })
                    .flatMap(Function.identity()))
        .collect(
            Collectors.groupingBy(
                Pair::first, Collectors.mapping(Pair::second, Collectors.joining(",", "{", "}"))))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining(",", "{", "}"));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return contents.stream()
            .filter(
                content ->
                    content.second().stream()
                            .filter(expression -> expression.resolve(defs, errorHandler))
                            .count()
                        == content.second().size())
            .count()
        == contents.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    var ok =
        contents.stream()
                .filter(
                    content ->
                        content.first().resolve(expressionCompilerServices, errorHandler)
                            & content.second().stream()
                                    .filter(
                                        expression ->
                                            expression.resolveDefinitions(
                                                expressionCompilerServices, errorHandler))
                                    .count()
                                == content.second().size())
                .count()
            == contents.size();
    final var counts =
        contents.stream()
            .map(c -> c.second().size())
            .collect(Collectors.toCollection(TreeSet::new));
    if (counts.size() != 1) {
      errorHandler.accept(
          String.format(
              "%d:%d: Inconsistent number of items between branches: %s",
              line(),
              column(),
              counts.stream().map(Object::toString).collect(Collectors.joining(", "))));
      ok = false;
    }
    if (ok) {
      final var names =
          contents.stream()
              .flatMap(p -> p.first().targets())
              .map(Target::name)
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
      for (final var name : names.entrySet()) {
        if (name.getValue() > 1) {
          errorHandler.accept(
              String.format(
                  "%d:%d: Name %s is repeated %d times.",
                  line(), column(), name.getKey(), name.getValue()));
          ok = false;
        }
      }
    }
    final var wildcards =
        contents.stream()
            .map(c -> c.first().checkWildcard(errorHandler))
            .collect(Collectors.toSet());
    if (wildcards.contains(WildcardCheck.HAS_WILDCARD)) {
      errorHandler.accept(String.format("%d:%d: Wildcards are not allowed", line(), column()));
      ok = false;
    } else if (wildcards.contains(WildcardCheck.BAD)) {
      ok = false;
    }
    return ok;
  }

  @Override
  public Imyhat type() {
    return new Imyhat.ObjectImyhat(
        contents.stream()
            .flatMap(c -> c.first().targets())
            .map(t -> new Pair<>(t.name(), Imyhat.dictionary(Imyhat.STRING, t.type()))));
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = true;
    for (final var content : contents) {
      if (content.second().stream().filter(e -> e.typeCheck(errorHandler)).count()
          == content.second().size()) {
        var type = content.second().get(0).type();
        for (int i = 1; i < content.second().size(); i++) {
          final var nextType = content.second().get(i).type();
          if (nextType.isSame(type)) {
            type = type.unify(nextType);
          } else {
            content.second().get(i).typeError(type, nextType, errorHandler);
            ok = false;
            break;
          }
        }
        if (ok) {
          ok = content.first().typeCheck(type, errorHandler);
        }
      } else {
        ok = false;
      }
    }
    return ok;
  }
}
