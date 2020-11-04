package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class WizardNodeSwitch extends WizardNode {

  private final WizardNode alternative;
  private final List<Pair<ExpressionNode, WizardNode>> cases;

  private final ExpressionNode test;

  public WizardNodeSwitch(
      ExpressionNode test, List<Pair<ExpressionNode, WizardNode>> cases, WizardNode alternative) {
    this.test = test;
    this.cases = cases;
    this.alternative = alternative;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer, EcmaLoadableConstructor name) {
    return renderer.newConst(
        renderer.lambda(
            1,
            (r, a) -> {
              name.create(rr -> a.apply(0)).forEach(r::define);
              final String testValue = r.newConst(test.renderEcma(r));
              final String testLambda =
                  r.newConst(
                      r.lambda(
                          1,
                          (rr, args) ->
                              test.type()
                                  .apply(EcmaScriptRenderer.isEqual(args.apply(0), testValue))));
              final String result = r.newLet();
              r.mapIf(
                  cases.stream(),
                  p -> String.format("%s(%s)", testLambda, p.first().renderEcma(r)),
                  (rr, p) ->
                      rr.statement(
                          String.format("%s = %s", result, p.second().renderEcma(renderer, name))),
                  rr ->
                      rr.statement(
                          String.format(
                              "%s = %s", result, alternative.renderEcma(renderer, name))));
              return String.format("%s(%s)", result, a.apply(0));
            }));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return test.resolve(defs, errorHandler)
        & cases
                .stream()
                .filter(
                    c ->
                        c.first().resolve(defs, errorHandler)
                            & c.second().resolve(defs, errorHandler))
                .count()
            == cases.size()
        & alternative.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return cases
                .stream()
                .filter(c -> c.second().resolveCrossReferences(references, errorHandler))
                .count()
            == cases.size()
        & alternative.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return test.resolveDefinitions(expressionCompilerServices, errorHandler)
        & cases
                .stream()
                .filter(
                    c ->
                        c.first().resolveDefinitions(expressionCompilerServices, errorHandler)
                            & c.second()
                                .resolveDefinitions(
                                    expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == cases.size()
        & alternative.resolveDefinitions(
            expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        test.typeCheck(errorHandler)
            & cases
                    .stream()
                    .filter(
                        c -> c.first().typeCheck(errorHandler) & c.second().typeCheck(errorHandler))
                    .count()
                == cases.size()
            & alternative.typeCheck(errorHandler);
    if (ok) {
      ok =
          cases
                  .stream()
                  .filter(
                      c -> {
                        if (c.first().type().isSame(test.type())) {
                          return true;
                        } else {
                          c.first().typeError(test.type(), c.first().type(), errorHandler);
                          return false;
                        }
                      })
                  .count()
              == cases.size();
    }
    return ok;
  }
}
