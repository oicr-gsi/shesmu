package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardNodeMatch extends WizardNode {

  private final WizardMatchAlternativeNode alternative;
  private final List<WizardMatchBranchNode> cases;
  private final int line;
  private final int column;
  private final ExpressionNode test;

  public WizardNodeMatch(
      int line,
      int column,
      ExpressionNode test,
      List<WizardMatchBranchNode> cases,
      WizardMatchAlternativeNode alternative) {
    this.line = line;
    this.column = column;
    this.test = test;
    this.cases = cases;
    this.alternative = alternative;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final var testValue = renderer.newConst(test.renderEcma(renderer));
    final var result = renderer.newLet();
    renderer.mapIf(
        cases.stream(),
        m -> {
          try {
            return String.format(
                "%s.type == %s", testValue, RuntimeSupport.MAPPER.writeValueAsString(m.name()));
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        },
        (r, m) -> r.statement(String.format("%s = %s", result, m.renderEcma(r, testValue))),
        r -> r.statement(String.format("%s = %s", result, alternative.render(r, testValue))));
    return result;
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return test.resolve(defs, errorHandler)
        & cases.stream().filter(c -> c.resolve(defs, errorHandler)).count() == cases.size()
        & alternative.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return cases.stream().filter(c -> c.resolveCrossReferences(references, errorHandler)).count()
            == cases.size()
        & alternative.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    var ok = true;
    final var caseCounts =
        cases.stream()
            .collect(Collectors.groupingBy(WizardMatchBranchNode::name, Collectors.counting()));
    for (final var entry : caseCounts.entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: %d branches for “%s”. Only one is allowed.",
                line, column, entry.getValue(), entry.getKey()));
        ok = false;
      }
    }
    return ok
        & test.resolveDefinitions(expressionCompilerServices, errorHandler)
        & cases.stream()
                .filter(
                    c ->
                        c.resolveDefinitions(
                            expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == cases.size()
        & alternative.resolveDefinitions(
            expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (test.typeCheck(errorHandler)) {
      final var requiredBranches =
          test.type()
              .apply(
                  new ImyhatTransformer<Map<String, Imyhat>>() {
                    @Override
                    public Map<String, Imyhat> algebraic(Stream<AlgebraicTransformer> contents) {
                      return contents
                          .map(
                              c ->
                                  c.visit(
                                      new AlgebraicVisitor<Pair<String, Imyhat>>() {
                                        @Override
                                        public Pair<String, Imyhat> empty(String name) {
                                          return new Pair<>(name, Imyhat.NOTHING);
                                        }

                                        @Override
                                        public Pair<String, Imyhat> object(
                                            String name, Stream<Pair<String, Imyhat>> contents) {
                                          return new Pair<>(name, new ObjectImyhat(contents));
                                        }

                                        @Override
                                        public Pair<String, Imyhat> tuple(
                                            String name, Stream<Imyhat> contents) {
                                          return new Pair<>(
                                              name, Imyhat.tuple(contents.toArray(Imyhat[]::new)));
                                        }
                                      }))
                          .collect(Collectors.toMap(Pair::first, Pair::second));
                    }

                    @Override
                    public Map<String, Imyhat> bool() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> date() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> floating() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> integer() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> json() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> list(Imyhat inner) {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> map(Imyhat key, Imyhat value) {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> object(Stream<Pair<String, Imyhat>> contents) {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> optional(Imyhat inner) {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> path() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> string() {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }

                    @Override
                    public Map<String, Imyhat> tuple(Stream<Imyhat> contents) {
                      test.typeError("algebraic type", test.type(), errorHandler);
                      return null;
                    }
                  });
      if (requiredBranches == null) {
        return false;
      }
      return cases.stream()
                  .filter(
                      c -> {
                        final var branchType = requiredBranches.get(c.name());
                        if (branchType == null) {
                          errorHandler.accept(
                              String.format(
                                  "%d:%d: Branch “%s” is not present in %s being matched.",
                                  line, column, c.name(), test.type().name()));
                          return false;
                        }
                        requiredBranches.remove(c.name());
                        return c.typeCheck(branchType, errorHandler);
                      })
                  .count()
              == cases.size()
          && alternative.typeCheck(line, column, requiredBranches, errorHandler);
    } else {
      return false;
    }
  }
}
