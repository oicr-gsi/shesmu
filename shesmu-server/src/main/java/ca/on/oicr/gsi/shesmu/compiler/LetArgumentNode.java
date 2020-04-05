package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class LetArgumentNode implements UndefinedVariableProvider {
  public static Parser parse(Parser input, Consumer<LetArgumentNode> output) {
    final Parser gangParser = input.whitespace().symbol("@");
    if (gangParser.isGood()) {
      final AtomicReference<String> name = new AtomicReference<>();
      final Parser gangResult = gangParser.whitespace().identifier(name::set).whitespace();
      if (gangResult.isGood()) {
        output.accept(new LetArgumentNodeGang(input.line(), input.column(), name.get()));
      }
      return gangResult;
    }

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
      return result;
    } else {
      final AtomicReference<String> loneName = new AtomicReference<>();
      final Parser justName = input.whitespace().identifier(loneName::set).whitespace();
      if (justName.isGood()) {
        output.accept(
            new LetArgumentNodeSimple(
                new DestructuredArgumentNodeVariable(loneName.get()),
                new ExpressionNodeVariable(input.line(), input.column(), loneName.get())));
        return justName;
      }
    }
    return result;
  }

  private static final Pattern UNWRAP = Pattern.compile("(OnlyIf|Univalued|)");

  public abstract boolean blankCheck(Consumer<String> errorHandler);

  public abstract WildcardCheck checkWildcard(Consumer<String> errorHandler);

  public abstract void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract boolean filters();

  public abstract void render(LetBuilder let);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Stream<Target> targets();

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
