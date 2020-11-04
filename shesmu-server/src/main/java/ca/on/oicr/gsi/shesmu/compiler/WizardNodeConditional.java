package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.function.Consumer;

public class WizardNodeConditional extends WizardNode {

  private final WizardNode falseStep;
  private final ExpressionNode test;
  private final WizardNode trueStep;

  public WizardNodeConditional(ExpressionNode test, WizardNode trueStep, WizardNode falseStep) {
    this.test = test;
    this.trueStep = trueStep;
    this.falseStep = falseStep;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer, EcmaLoadableConstructor name) {
    return renderer.newConst(
        renderer.lambda(
            1,
            (r, a) -> {
              name.create(rr -> a.apply(0)).forEach(r::define);
              final String result = r.newLet();
              r.conditional(
                  test.renderEcma(r),
                  (rr) ->
                      rr.statement(
                          String.format("%s = %s", result, trueStep.renderEcma(renderer, name))),
                  rr ->
                      rr.statement(
                          String.format("%s = %s", result, falseStep.renderEcma(renderer, name))));
              return String.format("%s(%s)", result, a.apply(0));
            }));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return test.resolve(defs, errorHandler)
        & trueStep.resolve(defs, errorHandler)
        & falseStep.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return trueStep.resolveCrossReferences(references, errorHandler)
        & falseStep.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return test.resolveDefinitions(expressionCompilerServices, errorHandler)
        & trueStep.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler)
        & falseStep.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = trueStep.typeCheck(errorHandler) & falseStep.typeCheck(errorHandler);
    if (test.typeCheck(errorHandler)) {
      if (test.type().isSame(Imyhat.BOOLEAN)) {
        return ok;
      } else {
        test.typeError(Imyhat.BOOLEAN, test.type(), errorHandler);
      }
    }
    return false;
  }
}
