package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** The arguments defined in the “With” section of a “Run” olive. */
public abstract class OliveArgumentNode {
  public static Parser parse(Parser input, Consumer<OliveArgumentNode> output) {
    final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
    final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
    final AtomicReference<ExpressionNode> condition = new AtomicReference<>();

    final Parser result =
        input
            .whitespace()
            .then(DestructuredArgumentNode::parse, name::set)
            .whitespace()
            .symbol("=")
            .whitespace()
            .then(ExpressionNode::parse, expression::set)
            .whitespace();
    final Parser conditionResult =
        result.keyword("If").whitespace().then(ExpressionNode::parse, condition::set).whitespace();
    if (conditionResult.isGood()) {
      output.accept(
          new OliveArgumentNodeOptional(
              input.line(), input.column(), name.get(), condition.get(), expression.get()));
      return conditionResult;
    }
    if (result.isGood()) {
      output.accept(
          new OliveArgumentNodeProvided(
              input.line(), input.column(), name.get(), expression.get()));
    }
    return result;
  }

  protected final int column;
  private final Map<String, ActionParameterDefinition> definitions = new HashMap<>();
  protected final int line;
  protected final DestructuredArgumentNode name;

  public OliveArgumentNode(int line, int column, DestructuredArgumentNode name) {
    this.line = line;
    this.column = column;
    this.name = name;
  }

  public final boolean checkArguments(
      Function<String, ActionParameterDefinition> parameterDefinitions,
      Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(
          String.format("%d:%d: Assignment in Monitor discards value.", line, column));
      return false;
    }
    return name.targets()
        .allMatch(
            target -> {
              final ActionParameterDefinition definition =
                  parameterDefinitions.apply(target.name());
              definitions.put(target.name(), definition);
              boolean ok = definition.type().isSame(target.type());
              if (!ok) {
                errorHandler.accept(
                    String.format(
                        "%d:%d: Expected argument “%s” to have type %s, but got %s.",
                        line,
                        column,
                        definition.name(),
                        definition.type().name(),
                        target.type().name()));
              }
              if (definition.required() && isConditional()) {
                errorHandler.accept(
                    String.format("%d:%d: Argument “%s” is required.", line, column, name));
                ok = false;
              }
              return ok;
            });
  }

  public boolean checkName(Consumer<String> errorHandler) {
    return name.typeCheck(type(), errorHandler);
  }

  public abstract void collectFreeVariables(
      Set<String> freeVariables, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  protected abstract boolean isConditional();

  /** Generate bytecode for this argument's value */
  public abstract void render(Renderer renderer, int action);

  /** Resolve variables in the expression of this argument */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve functions in this argument */
  public abstract boolean resolveFunctions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  protected void storeAll(Renderer renderer, int action, Consumer<Renderer> loadValue) {
    loadValue.accept(renderer);
    final int local = renderer.methodGen().newLocal(type().apply(TO_ASM));
    renderer.methodGen().storeLocal(local);
    name.render(r -> r.methodGen().loadLocal(local))
        .forEach(value -> definitions.get(value.name()).store(renderer, action, value));
  }

  /** The argument name */
  public final Stream<Target> targets() {
    return name.targets();
  }

  public abstract Imyhat type();

  /** Perform type check on this argument's expression */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
