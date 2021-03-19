package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ImyhatNodeAlgebraic extends ImyhatNode {

  private final int line;
  private final int column;
  private final List<AlgebraicImyhatNode> unions;

  public ImyhatNodeAlgebraic(int line, int column, List<AlgebraicImyhatNode> unions) {
    super();
    this.line = line;
    this.column = column;
    this.unions = unions;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var types =
        unions.stream()
            .map(n -> n.render(expressionCompilerServices, errorHandler))
            .collect(Collectors.toList());
    if (types.isEmpty() || types.stream().anyMatch(Imyhat::isBad)) {
      return Imyhat.BAD;
    }
    var result = types.get(0);
    for (var i = 1; i < types.size(); i++) {
      if (result.isSame(types.get(i))) {
        result = result.unify(types.get(i));
      } else {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot include %s with %s.",
                line, column, types.get(i).name(), result.name()));
        return Imyhat.BAD;
      }
    }
    return result;
  }
}
