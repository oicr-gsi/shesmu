package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class LetArgumentNode {
  public static Parser parse(Parser input, Consumer<LetArgumentNode> output) {
    final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
    final AtomicReference<BiFunction<DestructuredArgumentNode, ExpressionNode, LetArgumentNode>>
        unwrap = new AtomicReference<>();
    final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
    final Parser result =
        input
            .whitespace()
            .then(DestructuredArgumentNode::parse, name::set)
            .whitespace()
            .symbol("=")
            .whitespace()
            .regex(
                UNWRAP,
                m -> {
                  switch (m.group(0)) {
                    case "OnlyIf":
                      unwrap.set(LetArgumentNodeOptional::new);
                      return;
                    case "Univalued":
                      unwrap.set(LetArgumentNodeUnivalued::new);
                      return;
                    default:
                      unwrap.set(LetArgumentNodeSimple::new);
                  }
                },
                "“OnlyIf” , “Univalued” or expression")
            .whitespace()
            .then(ExpressionNode::parse, expression::set)
            .whitespace();

    if (result.isGood()) {
      output.accept(unwrap.get().apply(name.get(), expression.get()));
    }
    return result;
  }

  private static final Pattern UNWRAP = Pattern.compile("(OnlyIf|Univalued|)");
  private final ExpressionNode expression;
  private final DestructuredArgumentNode name;

  public LetArgumentNode(DestructuredArgumentNode name, ExpressionNode expression) {
    super();
    this.name = name;
    this.expression = expression;
    name.setFlavour(Target.Flavour.STREAM);
  }

  public final boolean blankCheck(Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Assignment in Let discards value.", expression.line(), expression.column()));
      return false;
    }
    return true;
  }

  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  public abstract boolean filters();

  protected abstract Consumer<Renderer> render(
      LetBuilder let, Imyhat type, Consumer<Renderer> loadLocal);

  public final void render(LetBuilder let) {
    final Consumer<Renderer> loadLocal =
        let.createLocal(expression.type().apply(TO_ASM), expression::render);
    name.render(render(let, expression.type(), loadLocal))
        .forEach(value -> let.add(value.type(), value.name(), value));
  }

  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  public final boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  public final Stream<Target> targets() {
    return name.targets();
  }

  protected abstract boolean typeCheck(
      int line,
      int column,
      Imyhat type,
      DestructuredArgumentNode name,
      Consumer<String> errorHandler);

  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    return typeCheck(expression.line(), expression.column(), expression.type(), name, errorHandler);
  }
}
