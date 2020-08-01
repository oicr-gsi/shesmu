package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.*;

public final class OliveClauseNodeLeftJoin extends OliveClauseNodeBaseLeftJoin {

  public OliveClauseNodeLeftJoin(
      int line,
      int column,
      String format,
      ExpressionNode outerKey,
      String variablePrefix,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    super(line, column, format, outerKey, variablePrefix, innerKey, children, where);
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
