package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public abstract class MatchBranchNode {
  private interface NodeConstructor {
    MatchBranchNode create(int line, int column, String name, ExpressionNode value);
  }

  private static final Type A_ALGEBRAIC_VALUE_TYPE = Type.getType(AlgebraicValue.class);
  public static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final ParseDispatch<NodeConstructor> CONSTRUCTOR = new ParseDispatch<>();
  private static final Method METHOD_ALGEBRAIC_TYPE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private static final Method METHOD_ALGEBRAIC_TYPE__NAME =
      new Method("name", A_STRING_TYPE, new Type[] {});
  private static final Method METHOD_OBJECT__EQUALS =
      new Method("equals", Type.BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});

  static {
    CONSTRUCTOR.addSymbol(
        "{",
        (p, o) ->
            DestructuredArgumentNode.parseTupleOrObject(
                p,
                o,
                f ->
                    (line, column, name, value) ->
                        new MatchBranchNodeObject(line, column, name, value, f),
                e ->
                    (line, column, name, value) ->
                        new MatchBranchNodeTuple(line, column, name, value, e)));
    CONSTRUCTOR.addKeyword(
        "_",
        (p, o) -> {
          o.accept(MatchBranchNodeDiscard::new);
          return p;
        });
    CONSTRUCTOR.addRaw(
        "nothing",
        (p, o) -> {
          o.accept(MatchBranchNodeEmpty::new);
          return p;
        });
  }

  public static Parser parse(Parser input, Consumer<MatchBranchNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<NodeConstructor> ctor = new AtomicReference<>();
    final AtomicReference<ExpressionNode> value = new AtomicReference<>();
    final Parser result =
        input
            .whitespace()
            .keyword("When")
            .whitespace()
            .algebraicIdentifier(name::set)
            .whitespace()
            .dispatch(CONSTRUCTOR, ctor::set)
            .whitespace()
            .keyword("Then")
            .whitespace()
            .then(ExpressionNode::parse0, value::set);
    if (result.isGood()) {
      output.accept(ctor.get().create(input.line(), input.column(), name.get(), value.get()));
    }
    return result;
  }

  private final int column;
  private final int line;
  private final String name;
  private final ExpressionNode value;

  protected MatchBranchNode(int line, int column, String name, ExpressionNode value) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.value = value;
  }

  protected abstract NameDefinitions bind(NameDefinitions definitions);

  protected abstract Stream<Target> boundNames();

  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final Set<String> remove =
        boundNames()
            .filter(n -> predicate.test(n.flavour()) && !names.contains(n.name()))
            .map(Target::name)
            .collect(Collectors.toSet());
    value.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    value.collectPlugins(pluginFileNames);
  }

  public final int column() {
    return column;
  }

  public final int line() {
    return line;
  }

  protected abstract Stream<EcmaLoadableValue> loadBoundNames(String base);

  public final String name() {
    return name;
  }

  protected abstract Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement);

  public void render(Renderer renderer, Label end, int local) {
    renderer.methodGen().loadLocal(local);
    renderer.methodGen().invokeVirtual(A_ALGEBRAIC_VALUE_TYPE, METHOD_ALGEBRAIC_TYPE__NAME);
    renderer.methodGen().push(name);
    renderer.methodGen().invokeVirtual(A_STRING_TYPE, METHOD_OBJECT__EQUALS);
    final Label next = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, next);
    value.render(
        prepare(
            renderer,
            (r, i) -> {
              r.methodGen().loadLocal(local);
              r.methodGen().push(i);
              r.methodGen().invokeVirtual(A_ALGEBRAIC_VALUE_TYPE, METHOD_ALGEBRAIC_TYPE__GET);
            }));
    renderer.methodGen().goTo(end);
    renderer.methodGen().mark(next);
  }

  public String renderEcma(EcmaScriptRenderer renderer, String original) {
    loadBoundNames(original).forEach(renderer::define);
    return value.renderEcma(renderer);
  }

  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return value.resolve(bind(defs), errorHandler);
  }

  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return value.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public final Imyhat resultType() {
    return value.type();
  }

  public final boolean typeCheck(Imyhat argumentType, Consumer<String> errorHandler) {
    return typeCheckBindings(argumentType, errorHandler) && value.typeCheck(errorHandler);
  }

  protected abstract boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler);

  public final void typeError(Imyhat expected, Imyhat found, Consumer<String> errorHandler) {
    value.typeError(expected, found, errorHandler);
  }
}
