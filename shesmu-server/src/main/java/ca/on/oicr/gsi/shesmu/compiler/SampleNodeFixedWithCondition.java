package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.subsample.FixedWithConditions;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Subsampler;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SampleNodeFixedWithCondition extends SampleNode {

  private static final Type A_FIXEDWITHCONDITION_TYPE = Type.getType(FixedWithConditions.class);

  private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
  private static final Method CTOR =
      new Method(
          "<init>",
          Type.VOID_TYPE,
          new Type[] {Type.getType(Subsampler.class), Type.LONG_TYPE, A_PREDICATE_TYPE});
  private final ExpressionNode conditionExpression;
  private final ExpressionNode limitExpression;

  private List<String> definedNames;

  private Imyhat type;

  public SampleNodeFixedWithCondition(
      ExpressionNode limitExpression, ExpressionNode conditionExpression) {
    this.limitExpression = limitExpression;
    this.conditionExpression = conditionExpression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    limitExpression.collectFreeVariables(names, predicate);
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    conditionExpression.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    limitExpression.collectPlugins(pluginFileNames);
    conditionExpression.collectPlugins(pluginFileNames);
  }

  @Override
  public Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler) {
    switch (previous) {
      case LIMITED:
        return Consumption.LIMITED;
      case GREEDY:
        errorHandler.accept(
            String.format(
                "%d:%d: No items will be left to subsample.",
                limitExpression.line(), limitExpression.column()));
        return Consumption.BAD;
      case BAD:
      default:
        return Consumption.BAD;
    }
  }

  @Override
  public void render(
      Renderer renderer, int previousLocal, Imyhat currentType, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    conditionExpression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final LambdaBuilder builder =
        new LambdaBuilder(
            renderer.root(),
            String.format(
                "For ⋯ Subsample ⋯ %d:%d",
                conditionExpression.line(), conditionExpression.column()),
            LambdaBuilder.predicate(currentType),
            renderer.streamType(),
            renderer
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));

    final Renderer conditionRenderer = builder.renderer(renderer.signerEmitter());
    final int argCount = conditionRenderer.methodGen().getArgumentTypes().length;
    name.create(r -> r.methodGen().loadArg(argCount - 1))
        .forEach(v -> conditionRenderer.define(v.name(), v));

    conditionRenderer.methodGen().visitCode();
    conditionExpression.render(conditionRenderer);
    conditionRenderer.methodGen().returnValue();
    conditionRenderer.methodGen().endMethod();

    renderer.methodGen().newInstance(A_FIXEDWITHCONDITION_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().loadLocal(previousLocal);
    limitExpression.render(renderer);
    builder.push(renderer);
    renderer.methodGen().invokeConstructor(A_FIXEDWITHCONDITION_TYPE, CTOR);
    renderer.methodGen().storeLocal(previousLocal);
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final boolean ok =
        limitExpression.resolve(defs, errorHandler)
            & conditionExpression.resolve(defs.bind(name), errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return limitExpression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & conditionExpression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    this.type = type;
    boolean limitok = limitExpression.typeCheck(errorHandler);
    boolean conditionok = conditionExpression.typeCheck(errorHandler);
    if (limitok && !limitExpression.type().isSame(Imyhat.INTEGER)) {
      limitExpression.typeError(Imyhat.INTEGER, limitExpression.type(), errorHandler);
      limitok = false;
    }
    if (conditionok && !conditionExpression.type().isSame(Imyhat.BOOLEAN)) {
      conditionExpression.typeError(Imyhat.BOOLEAN, conditionExpression.type(), errorHandler);
      conditionok = false;
    }
    return limitok & conditionok;
  }
}
