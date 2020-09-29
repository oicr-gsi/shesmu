package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** The arguments defined in the “With” section of a “Run” olive. */
public abstract class OliveArgumentNode implements UndefinedVariableProvider {
  private interface ArgumentStorer {

    void store(Renderer renderer, int action, LoadableValue value);
  }

  private static class OptionalProvided implements ArgumentStorer {
    private final ActionParameterDefinition parameterDefinition;

    private OptionalProvided(ActionParameterDefinition parameterDefinition) {
      this.parameterDefinition = parameterDefinition;
    }

    @Override
    public void store(Renderer renderer, int action, LoadableValue value) {
      value.accept(renderer);
      renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
      final Label end = renderer.methodGen().newLabel();
      renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, end);
      parameterDefinition.store(
          renderer,
          action,
          new LoadableValue() {
            @Override
            public void accept(Renderer renderer) {
              value.accept(renderer);
              renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__GET);
              renderer.methodGen().unbox(parameterDefinition.type().apply(TO_ASM));
            }

            @Override
            public String name() {
              return value.name() + " inner";
            }

            @Override
            public Type type() {
              return parameterDefinition.type().apply(TO_ASM);
            }
          });
      renderer.methodGen().mark(end);
    }
  }

  private static class Unmodified implements ArgumentStorer {
    private final ActionParameterDefinition parameterDefinition;

    private Unmodified(ActionParameterDefinition parameterDefinition) {
      this.parameterDefinition = parameterDefinition;
    }

    @Override
    public void store(Renderer renderer, int action, LoadableValue value) {
      parameterDefinition.store(renderer, action, value);
    }
  }

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__GET =
      new Method("get", Type.getType(Object.class), new Type[0]);
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[0]);

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
  private final Map<String, ArgumentStorer> definitions = new HashMap<>();
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
      errorHandler.accept(String.format("%d:%d: Assignment discards value.", line, column));
      return false;
    }
    return name.targets()
        .allMatch(
            target -> {
              final ActionParameterDefinition definition =
                  parameterDefinitions.apply(target.name());

              if (definition.required() && isConditional()) {
                errorHandler.accept(
                    String.format("%d:%d: Argument “%s” is required.", line, column, name));
                return false;
              } else if (definition.type().isAssignableFrom(target.type())) {
                definitions.put(target.name(), new Unmodified(definition));
                return true;
              } else if (!definition.required()
                  && definition.type().asOptional().isAssignableFrom(target.type())) {
                definitions.put(target.name(), new OptionalProvided(definition));
                return true;
              } else {
                ExpressionNode.generateTypeError(
                    line,
                    column,
                    String.format(" for argument “%s”", definition.name()),
                    definition.type(),
                    target.type(),
                    errorHandler);
                return false;
              }
            });
  }

  public boolean checkName(Consumer<String> errorHandler) {
    return name.typeCheck(type(), errorHandler);
  }

  public final WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return name.checkWildcard(errorHandler);
  }

  public abstract void collectFreeVariables(
      Set<String> freeVariables, Predicate<Flavour> predicate);

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return this.name.handleUndefinedVariable(name);
  }

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  protected abstract boolean isConditional();

  /** Generate bytecode for this argument's value */
  public abstract void render(Renderer renderer, int action);

  /** Resolve variables in the expression of this argument */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveExtraFunctions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  /** Resolve functions in this argument */
  public final boolean resolveFunctions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return name.resolve(oliveCompilerServices, errorHandler)
        & resolveExtraFunctions(oliveCompilerServices, errorHandler);
  }

  protected void storeAll(Renderer renderer, int action, Consumer<Renderer> loadValue) {
    loadValue.accept(renderer);
    final int local = renderer.methodGen().newLocal(type().apply(TO_ASM));
    renderer.methodGen().storeLocal(local);
    name.render(r -> r.methodGen().loadLocal(local))
        .forEach(value -> definitions.get(value.name()).store(renderer, action, value));
  }

  /** The argument name */
  public final Stream<DefinedTarget> targets() {
    return name.targets();
  }

  public abstract Imyhat type();

  /** Perform type check on this argument's expression */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
