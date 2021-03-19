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
  private static final Parser.ParseDispatch<ComplexConstructor> COMPLEX =
      new Parser.ParseDispatch<>();

  private interface ComplexConstructor {
    DiscriminatorNode create(
        int line, int column, DestructuredArgumentNode name, ExpressionNode expression);
  }

  static {
    COMPLEX.addKeyword("OnlyIf", Parser.justWhiteSpace(DiscriminatorNodeOnlyIf::new));
    COMPLEX.addKeyword("Univalued", Parser.justWhiteSpace(DiscriminatorNodeUnivalued::new));
    COMPLEX.addRaw("rename", Parser.justWhiteSpace(DiscriminatorNodeRename::new));
  }

  public static Parser parse(Parser input, Consumer<DiscriminatorNode> output) {
    final var groupParser = input.whitespace().symbol("@");
    if (groupParser.isGood()) {
      final var name = new AtomicReference<String>();
      final var groupResult = groupParser.whitespace().identifier(name::set).whitespace();
      if (groupResult.isGood()) {
        output.accept(new DiscriminatorNodeGang(input.line(), input.column(), name.get()));
      }
      return groupResult;
    }
    final var complexName = new AtomicReference<DestructuredArgumentNode>();
    final var complexParser =
        input
            .whitespace()
            .then(DestructuredArgumentNode::parse, complexName::set)
            .whitespace()
            .symbol("=")
            .whitespace();
    if (complexParser.isGood()) {
      final var constructor = new AtomicReference<ComplexConstructor>();
      final var expression = new AtomicReference<ExpressionNode>();
      final var result =
          complexParser
              .dispatch(COMPLEX, constructor::set)
              .whitespace()
              .then(ExpressionNode::parse, expression::set)
              .whitespace();
      if (result.isGood()) {
        output.accept(
            constructor
                .get()
                .create(input.line(), input.column(), complexName.get(), expression.get()));
      }
      return result;
    } else {
      final var name = new AtomicReference<String>();
      final var result = input.whitespace().identifier(name::set).whitespace();
      if (result.isGood()) {
        output.accept(new DiscriminatorNodeSimple(input.line(), input.column(), name.get()));
      }
      return result;
    }
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
