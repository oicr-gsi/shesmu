package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public final class DefinedCheckNodeConstant extends DefinedCheckNode {
  private final String name;

  public DefinedCheckNodeConstant(String name) {
    this.name = name;
  }

  public boolean check(ExpressionCompilerServices expressionCompilerServices) {
    return true;
  }

  public boolean check(NameDefinitions defs) {
    return defs.get(name).map(v -> v.flavour() == Flavour.CONSTANT).orElse(false);
  }
}
