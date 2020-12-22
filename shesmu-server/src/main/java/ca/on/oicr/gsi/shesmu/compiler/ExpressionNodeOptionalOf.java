package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeOptionalOf extends ExpressionNode {

  /**
   * For each question mark we encounter, we need to track the question marks inside of it. To do
   * this, we divide the operations into layers of nesting. Each layer is defined by an integer
   * number that starts at zero and decreases for every inner layer. Processing is done in layer
   * order.
   *
   * <p>Given <tt>foo(x?)? + y?</tt>, this will be divided into two layers (plus the base
   * expression):
   *
   * <ul>
   *   <li>Base expression: <tt>$0 + $1</tt>
   *   <li>Layer 0: <tt>$0 = foo($2)</tt> and <tt>$1 = y</tt>
   *   <li>Layer -1: <tt>$2 = x</tt>
   * </ul>
   *
   * The order of the expressions in each layer is arbitrary, but there is no way for them to
   * interfere, so it doesn't matter.
   */
  private class OptionalCaptureCompilerServices implements ExpressionCompilerServices {
    private final Consumer<String> errorHandler;
    private final ExpressionCompilerServices expressionCompilerServices;
    private final int layer;

    public OptionalCaptureCompilerServices(
        ExpressionCompilerServices expressionCompilerServices,
        Consumer<String> errorHandler,
        int layer) {
      this.expressionCompilerServices = expressionCompilerServices;
      this.errorHandler = errorHandler;
      this.layer = layer;
    }

    @Override
    public Optional<TargetWithContext> captureOptional(ExpressionNode expression) {
      if (expression.resolveDefinitions(
          new OptionalCaptureCompilerServices(expressionCompilerServices, errorHandler, layer - 1),
          errorHandler)) {
        final UnboxableExpression target = new UnboxableExpression(expression);
        captures.computeIfAbsent(layer, k -> new ArrayList<>()).add(target);
        return Optional.of(target);
      } else {
        return Optional.empty();
      }
    }

    @Override
    public FunctionDefinition function(String name) {
      return expressionCompilerServices.function(name);
    }

    @Override
    public InputFormatDefinition inputFormat(String format) {
      return expressionCompilerServices.inputFormat(format);
    }

    @Override
    public Imyhat imyhat(String name) {
      return expressionCompilerServices.imyhat(name);
    }

    @Override
    public InputFormatDefinition inputFormat() {
      return expressionCompilerServices.inputFormat();
    }
  }

  private static class UnboxableExpression implements TargetWithContext {
    private Optional<NameDefinitions> defs = Optional.empty();
    private final ExpressionNode expression;
    private final String name;

    public UnboxableExpression(ExpressionNode expression) {
      this.expression = expression;
      name = String.format("Lift of %d:%d", expression.line(), expression.column());
    }

    @Override
    public Flavour flavour() {
      return Flavour.LAMBDA;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void read() {
      // Super. Don't care.
    }

    @Override
    public void setContext(NameDefinitions defs) {
      this.defs = Optional.of(defs);
    }

    @Override
    public Imyhat type() {
      return expression.type() instanceof Imyhat.OptionalImyhat
          ? ((Imyhat.OptionalImyhat) expression.type()).inner()
          : expression.type();
    }
  }

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__GET = new Method("get", A_OBJECT_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__OF =
      new Method("of", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});
  private final Map<Integer, List<UnboxableExpression>> captures = new TreeMap<>();
  private final ExpressionNode item;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeOptionalOf(int line, int column, ExpressionNode item) {
    super(line, column);
    this.item = item;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    for (final List<UnboxableExpression> layer : captures.values()) {
      for (final UnboxableExpression capture : layer) {
        capture.expression.collectFreeVariables(names, predicate);
      }
    }
    item.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final List<UnboxableExpression> layer : captures.values()) {
      for (final UnboxableExpression capture : layer) {
        capture.expression.collectPlugins(pluginFileNames);
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
      final Label end = renderer.methodGen().newLabel();
      final Label empty = renderer.methodGen().newLabel();
      for (final List<UnboxableExpression> layer : captures.values()) {
        for (final UnboxableExpression capture : layer) {
          capture.expression.render(renderer);
          renderer.methodGen().dup();
          renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
          renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, empty);
          renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__GET);
          final Type type = capture.type().apply(TO_ASM);
          renderer.methodGen().unbox(type);
          final int local = renderer.methodGen().newLocal(type);
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
      item.render(renderer);
      // The value might already be wrapped in an optional; if it ins't do that now.
      if (!item.type().isSame(item.type().asOptional())) {
        renderer.methodGen().box(item.type().apply(TO_ASM));
        renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
      }
      renderer.methodGen().goTo(end);
      renderer.methodGen().mark(empty);
      renderer.methodGen().pop();
      renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
      renderer.methodGen().mark(end);
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
        && captures
            .values()
            .stream()
            .allMatch(
                layer ->
                    layer
                            .stream()
                            .filter(
                                capture ->
                                    capture.expression.resolve(
                                        capture.defs.map(defs::withShadowContext).orElse(defs),
                                        errorHandler))
                            .count()
                        == layer.size());
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return item.resolveDefinitions(
        new OptionalCaptureCompilerServices(expressionCompilerServices, errorHandler, 0),
        errorHandler);
  }

  @Override
  public Imyhat type() {
    return type.asOptional();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (captures.isEmpty()) {
      boolean ok = item.typeCheck(errorHandler);
      type = item.type();
      if (type.isSame(type.asOptional())) {
        item.typeError("non-optional", item.type(), errorHandler);
        ok = false;
      }
      return ok;
    } else {
      boolean ok =
          captures
                  .values()
                  .stream()
                  .allMatch(
                      layer ->
                          layer
                                  .stream()
                                  .filter(
                                      capture -> {
                                        boolean captureOk =
                                            capture.expression.typeCheck(errorHandler);
                                        if (!capture
                                            .expression
                                            .type()
                                            .isSame(capture.expression.type().asOptional())) {
                                          capture.expression.typeError(
                                              "optional", capture.expression.type(), errorHandler);
                                          captureOk = false;
                                        }
                                        return captureOk;
                                      })
                                  .count()
                              == layer.size())
              && item.typeCheck(errorHandler);
      type = item.type();
      return ok;
    }
  }
}
