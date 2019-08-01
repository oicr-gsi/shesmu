package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class CollectNodeWithDefault extends CollectNode {
  protected final ExpressionNode alternative;
  private List<String> definedNames;
  private Imyhat returnType = Imyhat.BAD;
  protected final ExpressionNode selector;
  private final String syntax;
  private Imyhat type;

  protected CollectNodeWithDefault(
      String syntax, int line, int column, ExpressionNode selector, ExpressionNode alternative) {
    super(line, column);
    this.syntax = syntax;
    this.selector = selector;
    this.alternative = alternative;
  }

  /** Add all free variable names to the set provided. */
  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    alternative.collectFreeVariables(names, predicate);
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    selector.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    alternative.collectPlugins(pluginFileNames);
    selector.collectPlugins(pluginFileNames);
  }

  protected abstract void finishMethod(Renderer renderer);

  protected final Imyhat incomingType() {
    return type;
  }

  protected abstract Pair<Renderer, Renderer> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables);

  @Override
  public final void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Pair<Renderer, Renderer> renderers =
        makeMethod(
            builder,
            name,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));

    renderers.first().methodGen().visitCode();
    selector.render(renderers.first());
    finishMethod(renderers.first());
    renderers.first().methodGen().returnValue();
    renderers.first().methodGen().visitMaxs(0, 0);
    renderers.first().methodGen().visitEnd();

    renderers.second().methodGen().visitCode();
    alternative.render(renderers.second());
    renderers.second().methodGen().returnValue();
    renderers.second().methodGen().visitMaxs(0, 0);
    renderers.second().methodGen().visitEnd();
  }

  /** Resolve all variable plugins in this expression and its children. */
  @Override
  public final boolean resolve(
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler) {
    definedNames = name.stream().map(Target::name).collect(Collectors.toList());
    return alternative.resolve(defs, errorHandler)
        & selector.resolve(defs.bind(name), errorHandler);
  }

  /** Resolve all functions plugins in this expression */
  @Override
  public final boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return alternative.resolveFunctions(definedFunctions, errorHandler)
        & selector.resolveFunctions(definedFunctions, errorHandler);
  }

  protected abstract Imyhat returnType(Imyhat incomingType, Imyhat selectorType);

  @Override
  public final Imyhat type() {
    return returnType;
  }

  @Override
  public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    type = incoming;
    if (selector.typeCheck(errorHandler) & alternative.typeCheck(errorHandler)) {
      returnType = returnType(incoming, selector.type());
      if (returnType.isSame(alternative.type())) {
        return typeCheckExtra(errorHandler);
      } else {
        errorHandler.accept(
            String.format(
                "%d:%d: %s would return %s, but default value is %s. They must be the same",
                line(), column(), syntax, returnType.name(), alternative.type().name()));
      }
    }
    return false;
  }

  /** Perform type checking on this expression. */
  protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
