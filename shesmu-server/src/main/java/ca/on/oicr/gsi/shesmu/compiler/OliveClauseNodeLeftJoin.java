package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.*;

public final class OliveClauseNodeLeftJoin extends OliveClauseNodeBaseLeftJoin {

  public OliveClauseNodeLeftJoin(
      Optional<String> label,
      int line,
      int column,
      JoinSourceNode source,
      ExpressionNode outerKey,
      String variablePrefix,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    super(label, line, column, source, outerKey, variablePrefix, innerKey, children, where);
  }

  @Override
  protected boolean intersection() {
    return false;
  }

  @Override
  protected String syntax() {
    return "LeftJoin";
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat type) {
    return Optional.empty();
  }
}
