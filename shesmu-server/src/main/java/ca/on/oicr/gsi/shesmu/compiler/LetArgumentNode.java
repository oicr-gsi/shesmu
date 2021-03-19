package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class LetArgumentNode implements UndefinedVariableProvider {
  public static Parser parse(Parser input, Consumer<LetArgumentNode> output) {
    final var gangParser = input.whitespace().symbol("@");
    if (gangParser.isGood()) {
      final var name = new AtomicReference<String>();
      final var gangResult = gangParser.whitespace().identifier(name::set).whitespace();
      if (gangResult.isGood()) {
        output.accept(new LetArgumentNodeGang(input.line(), input.column(), name.get()));
      }
      return gangResult;
    }
    final var prefixParser = input.whitespace().keyword("Prefix");
    if (prefixParser.isGood()) {
      final var prefix = new AtomicReference<String>();
      final var names = new AtomicReference<List<String>>();
      final var prefixResult =
          prefixParser
              .whitespace()
              .list(names::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')
              .whitespace()
              .keyword("With")
              .whitespace()
              .identifier(prefix::set)
              .whitespace();
      if (prefixResult.isGood()) {
        output.accept(
            new LetArgumentNodePrefix(
                prefixParser.line(), prefixParser.column(), prefix.get(), names.get()));
      }
      return prefixResult;
    }

    final var name = new AtomicReference<DestructuredArgumentNode>();
    final var unwrap =
        new AtomicReference<
            BiFunction<DestructuredArgumentNode, ExpressionNode, LetArgumentNode>>();
    final var expression = new AtomicReference<ExpressionNode>();
    final var result =
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
      final var loneName = new AtomicReference<String>();
      final var justName = input.whitespace().identifier(loneName::set).whitespace();
      if (justName.isGood()) {
        output.accept(
            new LetArgumentNodeSimple(
                new DestructuredArgumentNodeVariable(input.line(), input.column(), loneName.get()),
                new ExpressionNodeVariable(input.line(), input.column(), loneName.get())));
        return justName;
      }
    }
    return result;
  }

  private static final Pattern UNWRAP = Pattern.compile("(OnlyIf|Univalued|)");

  public abstract boolean blankCheck(Consumer<String> errorHandler);

  public abstract boolean checkUnusedDeclarations(Consumer<String> errorHandler);

  public abstract WildcardCheck checkWildcard(Consumer<String> errorHandler);

  public abstract void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract boolean filters();

  public abstract void render(LetBuilder let);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Stream<DefinedTarget> targets();

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
