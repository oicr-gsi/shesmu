package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public abstract class CollectNodeOptional extends CollectNode {
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Method METHOD_RUNTIME_SUPPORT__UNNEST =
      new Method("unnest", A_OPTIONAL_TYPE, new Type[] {A_OPTIONAL_TYPE});
  private List<String> definedNames;
  private Imyhat returnType = Imyhat.BAD;
  protected final ExpressionNode selector;

  protected CollectNodeOptional(int line, int column, ExpressionNode selector) {
    super(line, column);
    this.selector = selector;
  }

  /** Add all free variable names to the set provided. */
  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    selector.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    selector.collectPlugins(pluginFileNames);
  }

  protected abstract void finishMethod(Renderer renderer);

  protected abstract Renderer makeMethod(
      JavaStreamBuilder builder,
      LoadableConstructor name,
      Imyhat returnType,
      LoadableValue[] loadables);

  @Override
  public final void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Renderer renderer =
        makeMethod(
            builder,
            name,
            returnType,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));

    renderer.methodGen().visitCode();
    selector.render(renderer);
    finishMethod(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();

    if (returnType == Imyhat.EMPTY || returnType instanceof Imyhat.OptionalImyhat) {
      builder
          .renderer()
          .methodGen()
          .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__UNNEST);
    }
  }

  /** Resolve all variable plugins in this expression and its children. */
  @Override
  public final boolean resolve(
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler) {
    definedNames = name.stream().map(Target::name).collect(Collectors.toList());
    return selector.resolve(defs.bind(name), errorHandler);
  }

  /** Resolve all functions plugins in this expression */
  @Override
  public final boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return selector.resolveFunctions(definedFunctions, errorHandler);
  }

  protected abstract Imyhat returnType(Imyhat incomingType, Imyhat selectorType);

  @Override
  public final Imyhat type() {
    return returnType.asOptional();
  }

  @Override
  public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    if (selector.typeCheck(errorHandler)) {
      returnType = returnType(incoming, selector.type());
      return true;
    }
    return false;
  }

  /** Perform type checking on this expression. */
  protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
