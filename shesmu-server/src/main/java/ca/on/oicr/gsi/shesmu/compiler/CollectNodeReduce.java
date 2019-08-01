package ca.on.oicr.gsi.shesmu.compiler;

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
import java.util.stream.Stream;

public class CollectNodeReduce extends CollectNode {

  private final DestructuredArgumentNode accumulatorName;
  private List<String> definedNames;
  private final ExpressionNode initial;

  private final ExpressionNode reducer;
  Imyhat resultType = Imyhat.BAD;
  Imyhat type;

  public CollectNodeReduce(
      int line,
      int column,
      DestructuredArgumentNode accumulatorName,
      ExpressionNode reducer,
      ExpressionNode initial) {
    super(line, column);
    this.accumulatorName = accumulatorName;
    this.reducer = reducer;
    this.initial = initial;
    accumulatorName.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    initial.collectFreeVariables(names, predicate);
    final List<String> remove =
        Stream.concat(accumulatorName.targets().map(Target::name), definedNames.stream())
            .filter(name -> !names.contains(name))
            .collect(Collectors.toList());
    reducer.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    initial.collectPlugins(pluginFileNames);
    reducer.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> capturedNames = new HashSet<>();
    reducer.collectFreeVariables(capturedNames, Flavour::needsCapture);
    accumulatorName.targets().map(Target::name).forEach(capturedNames::remove);
    capturedNames.removeAll(definedNames);
    final Renderer reducerRenderer =
        builder.reduce(
            line(),
            column(),
            name,
            initial.type(),
            accumulatorName::render,
            initial::render,
            builder
                .renderer()
                .allValues()
                .filter(v -> capturedNames.contains(v.name()))
                .toArray(LoadableValue[]::new));
    reducerRenderer.methodGen().visitCode();
    reducer.render(reducerRenderer);
    reducerRenderer.methodGen().returnValue();
    reducerRenderer.methodGen().visitMaxs(0, 0);
    reducerRenderer.methodGen().visitEnd();
  }

  @Override
  public boolean resolve(List<Target> name, NameDefinitions defs, Consumer<String> errorHandler) {
    definedNames = name.stream().map(Target::name).collect(Collectors.toList());
    return initial.resolve(defs, errorHandler)
        & reducer.resolve(
            defs.bind(name).bind(accumulatorName.targets().collect(Collectors.toList())),
            errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return initial.resolveFunctions(definedFunctions, errorHandler)
        & reducer.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return resultType;
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    type = incoming;
    boolean ok =
        initial.typeCheck(errorHandler)
            && accumulatorName.typeCheck(initial.type(), errorHandler)
            && reducer.typeCheck(errorHandler);
    if (ok) {
      if (!initial.type().isSame(reducer.type())) {
        errorHandler.accept(
            String.format(
                "%d:%d: Reducer produces type %s, but initial expression is %s.",
                line(), column(), reducer.type().name(), initial.type().name()));
        ok = false;
      } else {
        resultType = reducer.type().unify(initial.type());
      }
    }
    return ok;
  }
}
