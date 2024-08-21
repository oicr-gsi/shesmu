package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.util.Optional;

public class OliveClauseNodeIntersectionJoin extends OliveClauseNodeBaseJoin {

  public OliveClauseNodeIntersectionJoin(
      Optional<String> label,
      int line,
      int column,
      JoinSourceNode source,
      ExpressionNode outerKey,
      ExpressionNode innerKey) {
    super(label, line, column, source, outerKey, innerKey);
  }

  @Override
  public String syntax() {
    return "IntersectionJoin";
  }

  @Override
  protected Optional<JoinKind> typeCheckKeys(Imyhat outerKey, Imyhat innerKey) {
    if (innerKey.isSame(outerKey) && innerKey instanceof ListImyhat) {
      return Optional.of(JoinKind.INTERSECTION);
    }
    if (innerKey.isSame(outerKey.asList()) && innerKey instanceof ListImyhat) {
      return Optional.of(JoinKind.INTERSECTION_LIFT_OUTER);
    }
    if (innerKey.asList().isSame(outerKey) && outerKey instanceof ListImyhat) {
      return Optional.of(JoinKind.INTERSECTION_LIFT_INNER);
    }
    return Optional.empty();
  }
}
