package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** One of the <tt>By</tt> clauses in <tt>Group</tt> clause */
public abstract class DiscriminatorNode {

  public static Parser parse(Parser input, Consumer<DiscriminatorNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final Parser groupParser = input.whitespace().symbol("@");
    if (groupParser.isGood()) {
      final Parser groupResult = groupParser.whitespace().identifier(name::set).whitespace();
      if (groupResult.isGood()) {
        output.accept(new DiscriminatorNodeGang(input.line(), input.column(), name.get()));
      }
      return groupResult;
    }
    final Parser baseParser = input.whitespace().identifier(name::set).whitespace();
    if (baseParser.isGood()) {

      final Parser renameParser = baseParser.symbol("=");
      if (renameParser.isGood()) {
        final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
        final Parser renameResult =
            renameParser.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
        if (renameResult.isGood()) {
          output.accept(
              new DiscriminatorNodeRename(
                  input.line(), input.column(), name.get(), expression.get()));
        }
        return renameResult;
      }
      output.accept(new DiscriminatorNodeSimple(input.line(), input.column(), name.get()));
    }
    return baseParser;
  }

  public DiscriminatorNode() {
    super();
  }

  /** Add all free variable names to the set provided. */
  public abstract void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract Stream<VariableInformation> dashboard();

  /** Produce bytecode for this discriminator */
  public abstract void render(RegroupVariablesBuilder builder);

  /** Resolve all variable plugins in this discriminator and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all function plugins in this discriminator */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Stream<DefinedTarget> targets();

  /** Perform type checking on this discriminator and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
