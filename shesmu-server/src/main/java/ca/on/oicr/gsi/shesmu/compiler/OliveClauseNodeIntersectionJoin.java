package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.util.Optional;

public class OliveClauseNodeIntersectionJoin extends OliveClauseNodeBaseJoin {

  public OliveClauseNodeIntersectionJoin(
      int line,
      int column,
      JoinSourceNode source,
      ExpressionNode outerKey,
      ExpressionNode innerKey) {
    super(line, column, source, outerKey, innerKey);
  }

  @Override
  protected boolean intersection() {
    return true;
  }

  @Override
  public String syntax() {
    return "IntersectionJoin";
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat type) {
    return type instanceof ListImyhat ? Optional.empty() : Optional.of(type.asList());
  }
}
