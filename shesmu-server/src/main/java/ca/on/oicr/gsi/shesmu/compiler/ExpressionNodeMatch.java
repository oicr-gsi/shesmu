package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeMatch extends ExpressionNode {

  private static final Type A_ALGEBRAIC_VALUE_TYPE = Type.getType(AlgebraicValue.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method METHOD_ALGEBRAIC_VALUE__NAME =
      new Method("name", A_STRING_TYPE, new Type[] {});
  private static final Method METHOD__HASH_CODE =
      new Method("hashCode", Type.INT_TYPE, new Type[] {});
  private final MatchAlternativeNode alternative;
  private final List<MatchBranchNode> cases;
  private Imyhat resultType;
  private final ExpressionNode test;

  public ExpressionNodeMatch(
      int line,
      int column,
      ExpressionNode test,
      List<MatchBranchNode> cases,
      MatchAlternativeNode alternative) {
    super(line, column);
    this.test = test;
    this.cases = cases;
    this.alternative = alternative;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    test.collectFreeVariables(names, predicate);
    alternative.collectFreeVariables(names, predicate);
    cases.forEach(item -> item.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    test.collectPlugins(pluginFileNames);
    alternative.collectPlugins(pluginFileNames);
    cases.forEach(item -> item.collectPlugins(pluginFileNames));
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
  public void render(Renderer renderer) {
    test.render(renderer);
    final var local = renderer.methodGen().newLocal(test.type().apply(TypeUtils.TO_ASM));
    renderer.methodGen().storeLocal(local);

    final Map<Integer, List<MatchBranchNode>> paths =
        cases.stream()
            .collect(
                Collectors.groupingBy(b -> b.name().hashCode(), TreeMap::new, Collectors.toList()));

    final var alternativePath = renderer.methodGen().newLabel();
    final var pathValues = new int[paths.size()];
    final var pathLocations = new Label[paths.size()];
    var i = 0;
    for (final int value : paths.keySet()) {
      pathValues[i] = value;
      pathLocations[i] = renderer.methodGen().newLabel();
      i++;
    }

    final var end = renderer.methodGen().newLabel();
    renderer.methodGen().loadLocal(local);
    renderer.methodGen().invokeVirtual(A_ALGEBRAIC_VALUE_TYPE, METHOD_ALGEBRAIC_VALUE__NAME);
    renderer.methodGen().invokeVirtual(A_STRING_TYPE, METHOD__HASH_CODE);
    renderer.methodGen().visitLookupSwitchInsn(alternativePath, pathValues, pathLocations);
    for (i = 0; i < paths.size(); i++) {
      renderer.methodGen().mark(pathLocations[i]);
      for (final var branch : paths.get(pathValues[i])) {
        branch.render(renderer, end, local);
      }
      renderer.methodGen().goTo(alternativePath);
    }
    renderer.methodGen().mark(alternativePath);
    alternative.render(renderer, end, local);
    renderer.methodGen().mark(end);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return test.resolve(defs, errorHandler)
        & cases.stream().filter(c -> c.resolve(defs, errorHandler)).count() == cases.size()
        & alternative.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    var ok = true;
    final var caseCounts =
        cases.stream().collect(Collectors.groupingBy(MatchBranchNode::name, Collectors.counting()));
    for (final var entry : caseCounts.entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: %d branches for “%s”. Only one is allowed.",
                line(), column(), entry.getValue(), entry.getKey()));
        ok = false;
      }
    }
    return ok
        & test.resolveDefinitions(expressionCompilerServices, errorHandler)
        & cases.stream()
                .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == cases.size()
        & alternative.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return resultType == null ? Imyhat.BAD : resultType;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = test.typeCheck(errorHandler);
    if (ok) {
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
      ok =
          cases.stream()
                  .filter(
                      c -> {
                        final var branchType = requiredBranches.get(c.name());
                        if (branchType == null) {
                          errorHandler.accept(
                              String.format(
                                  "%d:%d: Branch “%s” is not present in %s being matched.",
                                  line(), column(), c.name(), test.type().name()));
                          return false;
                        }
                        requiredBranches.remove(c.name());
                        var isSame = c.typeCheck(branchType, errorHandler);
                        if (resultType == null) {
                          resultType = c.resultType();
                        } else if (c.resultType().isSame(resultType)) {
                          resultType = resultType.unify(c.resultType());
                        } else {
                          c.typeError(resultType, c.resultType(), errorHandler);
                          isSame = false;
                        }
                        return isSame;
                      })
                  .count()
              == cases.size();
      if (ok) {
        final var alternativeType =
            alternative.typeCheck(line(), column(), resultType, requiredBranches, errorHandler);
        if (alternativeType.isBad()) {
          return false;
        } else {
          resultType = alternativeType;
        }
      }
    }
    return ok;
  }
}
