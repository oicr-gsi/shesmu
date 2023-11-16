package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeOptionalOf extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__GET = new Method("get", A_OBJECT_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__OF =
      new Method("of", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});

  static void renderLayers(
      Renderer renderer, Label empty, Map<Integer, List<UnboxableExpression>> captures) {
    for (final var layer : captures.values()) {
      for (final var capture : layer) {
        final var optional = renderer.methodGen().newLocal(A_OPTIONAL_TYPE);
        capture.render(renderer);
        renderer.methodGen().dup();
        renderer.methodGen().storeLocal(optional);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
        renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, empty);
        renderer.methodGen().loadLocal(optional);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__GET);
        final var type = capture.type().apply(TO_ASM);
        renderer.methodGen().unbox(type);
        final var local = renderer.methodGen().newLocal(type);
        renderer.methodGen().storeLocal(local);
        renderer.define(
            capture.name(),
            new LoadableValue() {
              @Override
              public void accept(Renderer renderer) {
                renderer.methodGen().loadLocal(local);
              }

              @Override
              public String name() {
                return capture.name();
              }

              @Override
              public Type type() {
                return type;
              }
            });
      }
    }
  }

  private final Map<Integer, List<UnboxableExpression>> captures = new TreeMap<>();
  private final ExpressionNode item;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeOptionalOf(int line, int column, ExpressionNode item) {
    super(line, column);
    this.item = item;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    for (final var layer : captures.values()) {
      for (final var capture : layer) {
        capture.collectFreeVariables(names, predicate);
      }
    }
    item.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final var layer : captures.values()) {
      for (final var capture : layer) {
        capture.collectPlugins(pluginFileNames);
      }
    }
    item.collectPlugins(pluginFileNames);
  }

  @Override
  public Optional<String> dumpColumnName() {
    return item.dumpColumnName();
  }

  @Override
  public void render(Renderer renderer) {
    // Okay, we are going to build two radically different kinds of code. If we have an optional
    // with no captures, we just compute the value and stick it in an optional
    renderer.mark(line());
    if (captures.isEmpty()) {
      item.render(renderer);
      renderer.methodGen().box(type.apply(TO_ASM));
      renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
    } else {
      // If we have captures, we need to unbox all of them, store their guts, compute the result,
      // and then wrap it all back up in an optional (if the inner result isn't already)
      final var end = renderer.methodGen().newLabel();
      final var empty = renderer.methodGen().newLabel();
      renderLayers(renderer, empty, captures);
      item.render(renderer);
      // The value might already be wrapped in an optional; if it isn't do that now.
      if (!item.type().isSame(item.type().asOptional())) {
        renderer.methodGen().box(item.type().apply(TO_ASM));
        renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
      }
      renderer.methodGen().goTo(end);
      renderer.methodGen().mark(empty);
      renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
      renderer.methodGen().mark(end);
    }
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    if (captures.isEmpty()) {
      return item.renderEcma(renderer);
    } else {
      final var result = renderer.newLet("null");
      new LayerUnNester(captures.values().iterator(), item, result).accept(renderer);
      return result;
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    // In most of the passes, we evaluate the captured expressions first because they will be
    // evaluated first and the type information from them needs to flow into the inner expression.
    // However, since it looks like the capture expressions exist inside the inner expression, it's
    // possible to define a variable in the inner expression that the capture will be unable to
    // access.
    // We resolve variables inside the expression first because this
    // will define those variables and populate shadow contexts in the captures allowing us to
    // produce a useful error message
    // if a
    // capture tries to use a variable which is in scope at the point of capture but not in scope in
    // the current scope where the evaluation actually happens. See TargetWithContext for examples.
    return item.resolve(defs, errorHandler)
        && captures.values().stream()
            .allMatch(
                layer ->
                    layer.stream().filter(capture -> capture.resolve(defs, errorHandler)).count()
                        == layer.size());
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return item.resolveDefinitions(
        new OptionalCaptureCompilerServices(expressionCompilerServices, errorHandler, captures),
        errorHandler);
  }

  @Override
  public Imyhat type() {
    return type.asOptional();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (captures.isEmpty()) {
      var ok = item.typeCheck(errorHandler);
      type = item.type();
      if (type.isSame(type.asOptional())) {
        item.typeError("non-optional", item.type(), errorHandler);
        ok = false;
      }
      return ok;
    } else {
      var ok =
          captures.values().stream()
                  .allMatch(
                      layer ->
                          layer.stream().filter(capture -> capture.typeCheck(errorHandler)).count()
                              == layer.size())
              && item.typeCheck(errorHandler);
      type = item.type();
      return ok;
    }
  }
}
