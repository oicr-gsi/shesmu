package ca.on.oicr.gsi.shesmu.compiler;

public final class DefinedCheckNodeFunction extends DefinedCheckNode {
  private final String name;

  public DefinedCheckNodeFunction(String name) {
    this.name = name;
  }

  public boolean check(ExpressionCompilerServices expressionCompilerServices) {
    return expressionCompilerServices.function(name) != null;
  }

  public boolean check(NameDefinitions defs) {
    return true;
  }
}
