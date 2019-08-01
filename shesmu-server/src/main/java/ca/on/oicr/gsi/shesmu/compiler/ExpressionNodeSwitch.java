package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ExpressionNodeSwitch extends ExpressionNode {

  private interface CompareBrancher {
    public void branch(Label target, GeneratorAdapter methodGen);
  }

  private final ExpressionNode alternative;
  private final List<Pair<ExpressionNode, ExpressionNode>> cases;

  private final ExpressionNode test;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeSwitch(
      int line,
      int column,
      ExpressionNode test,
      List<Pair<ExpressionNode, ExpressionNode>> cases,
      ExpressionNode alternative) {
    super(line, column);
    this.test = test;
    this.cases = cases;
    this.alternative = alternative;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    test.collectFreeVariables(names, predicate);
    alternative.collectFreeVariables(names, predicate);
    cases.forEach(
        item -> {
          item.first().collectFreeVariables(names, predicate);
          item.second().collectFreeVariables(names, predicate);
        });
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    test.collectPlugins(pluginFileNames);
    alternative.collectPlugins(pluginFileNames);
    cases.forEach(
        item -> {
          item.first().collectPlugins(pluginFileNames);
          item.second().collectPlugins(pluginFileNames);
        });
  }

  @Override
  public void render(Renderer renderer) {
    CompareBrancher compare;
    if (test.type().isSame(Imyhat.BOOLEAN)) {
      compare = Comparison.EQ::branchBool;
    } else if (test.type().isSame(Imyhat.INTEGER)) {
      compare = Comparison.EQ::branchInt;
    } else {
      compare = Comparison.EQ::branchObject;
    }
    test.render(renderer);
    final int local = renderer.methodGen().newLocal(test.type().apply(TypeUtils.TO_ASM));
    renderer.methodGen().storeLocal(local);
    final Label end = renderer.methodGen().newLabel();
    final List<Runnable> createValues =
        cases
            .stream()
            .<Runnable>map(
                c -> {
                  renderer.methodGen().loadLocal(local);
                  c.first().render(renderer);
                  final Label emit = renderer.methodGen().newLabel();
                  compare.branch(emit, renderer.methodGen());
                  return () -> {
                    renderer.methodGen().mark(emit);
                    c.second().render(renderer);
                    renderer.methodGen().goTo(end);
                  };
                })
            .collect(Collectors.toList());
    alternative.render(renderer);
    renderer.methodGen().goTo(end);
    createValues.forEach(Runnable::run);
    renderer.methodGen().mark(end);
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
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return test.resolveFunctions(definedFunctions, errorHandler)
        & cases
                .stream()
                .filter(
                    c ->
                        c.first().resolveFunctions(definedFunctions, errorHandler)
                            & c.second().resolveFunctions(definedFunctions, errorHandler))
                .count()
            == cases.size()
        & alternative.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
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
      type = alternative.type();
      ok =
          cases
                  .stream()
                  .filter(
                      c -> {
                        boolean isSame = true;
                        if (!c.first().type().isSame(test.type())) {
                          c.first().typeError(test.type().name(), c.first().type(), errorHandler);
                          isSame = false;
                        }
                        if (c.second().type().isSame(type)) {
                          type = type.unify(c.second().type());
                        } else {
                          c.second().typeError(type.name(), c.second().type(), errorHandler);
                          isSame = false;
                        }
                        return isSame;
                      })
                  .count()
              == cases.size();
    }
    return ok;
  }
}
