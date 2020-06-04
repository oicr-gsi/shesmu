package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A collection action in a “Group” clause
 *
 * <p>Also usable as the variable definition for the result
 */
public final class GroupNodeLexicalConcat extends GroupNode {
  private final ExpressionNode expression;
  private final ExpressionNode delimiter;
  private final String name;
  private boolean read;

  public GroupNodeLexicalConcat(
      int line, int column, String name, ExpressionNode expression, ExpressionNode delimiter) {
    super(line, column);
    this.name = name;
    this.expression = expression;
    this.delimiter = delimiter;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    delimiter.collectFreeVariables(freeVariables, predicate);
    expression.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    delimiter.collectPlugins(pluginFileNames);
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isRead() {
    return read;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void read() {
    read = true;
  }

  @Override
  public void render(Regrouper regroup, RootBuilder rootBuilder) {
    regroup.addLexicalConcat(name(), expression::render, delimiter::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler) & delimiter.resolve(outerDefs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & delimiter.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.STRING;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final ExpressionNode e : new ExpressionNode[] {expression, delimiter}) {
      if (e.typeCheck(errorHandler)) {
        if (!e.type().isSame(Imyhat.STRING)) {
          e.typeError(Imyhat.STRING, e.type(), errorHandler);
          ok = false;
        }
      } else {
        ok = false;
      }
    }
    return ok;
  }
}
