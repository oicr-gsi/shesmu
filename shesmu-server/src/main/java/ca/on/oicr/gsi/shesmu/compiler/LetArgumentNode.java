package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LetArgumentNode {
  public static Parser parse(Parser input, Consumer<LetArgumentNode> output) {
    final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
    final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
    final Parser result =
        input
            .whitespace()
            .then(DestructuredArgumentNode::parse, name::set)
            .whitespace()
            .symbol("=")
            .whitespace()
            .then(ExpressionNode::parse, expression::set)
            .whitespace();

    if (result.isGood()) {
      output.accept(new LetArgumentNode(name.get(), expression.get()));
    }
    return result;
  }

  private final ExpressionNode expression;

  private final DestructuredArgumentNode name;

  public LetArgumentNode(DestructuredArgumentNode name, ExpressionNode expression) {
    super();
    this.name = name;
    this.expression = expression;
    name.setFlavour(Target.Flavour.STREAM);
  }

  public boolean blankCheck(Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Assignment in Let discards value.", expression.line(), expression.column()));
      return false;
    }
    return true;
  }

  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  public void render(LetBuilder let) {
    final Consumer<Renderer> loadLocal =
        let.createLocal(expression.type().apply(TypeUtils.TO_ASM), expression::render);
    name.render(loadLocal).forEach(value -> let.add(value.type(), value.name(), value));
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  public Stream<Target> targets() {
    return name.targets();
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler) && name.typeCheck(expression.type(), errorHandler);
  }
}
