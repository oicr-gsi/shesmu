package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.util.List;
import java.util.Optional;

public final class OliveClauseNodeLeftIntersectionJoin extends OliveClauseNodeBaseLeftJoin {

  public OliveClauseNodeLeftIntersectionJoin(
      int line,
      int column,
      JoinSourceNode source,
      ExpressionNode outerKey,
      String variablePrefix,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    super(line, column, source, outerKey, variablePrefix, innerKey, children, where);
  }

  @Override
  protected boolean intersection() {
    return true;
  }

  @Override
  protected String syntax() {
    return "LeftIntersectionJoin";
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat type) {
    return type instanceof ListImyhat ? Optional.empty() : Optional.of(type.asList());
  }
}
