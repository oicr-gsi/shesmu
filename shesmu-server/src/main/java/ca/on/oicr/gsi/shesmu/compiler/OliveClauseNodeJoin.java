package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;

public class OliveClauseNodeJoin extends OliveClauseNodeBaseJoin {

  public OliveClauseNodeJoin(
      int line, int column, String format, ExpressionNode outerKey, ExpressionNode innerKey) {
    super(line, column, format, outerKey, innerKey);
  }

  @Override
  protected boolean intersection() {
    return false;
  }

  @Override
  public String syntax() {
    return "Join";
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat type) {
    return Optional.empty();
  }
}
