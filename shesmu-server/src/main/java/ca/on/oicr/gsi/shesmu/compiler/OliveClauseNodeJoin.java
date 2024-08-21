package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;

public class OliveClauseNodeJoin extends OliveClauseNodeBaseJoin {

  public OliveClauseNodeJoin(
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
    return "Join";
  }

  @Override
  protected Optional<JoinKind> typeCheckKeys(Imyhat outerKey, Imyhat innerKey) {
    return innerKey.isSame(outerKey) ? Optional.of(JoinKind.DIRECT) : Optional.empty();
  }
}
