package ca.on.oicr.gsi.shesmu.compiler;

public abstract class GroupNodeDefaultable extends GroupNode {

  public GroupNodeDefaultable(int line, int column) {
    super(line, column);
  }

  public abstract void render(Regrouper regroup, ExpressionNode initial, RootBuilder builder);
}
