package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class DefinedCheckNode {
  private static final Parser.ParseDispatch<Function<String, DefinedCheckNode>> CHECK =
      new Parser.ParseDispatch<>();

  static {
    CHECK.addKeyword("Function", Parser.just(DefinedCheckNodeFunction::new));
    CHECK.addRaw("nothing", Parser.just(DefinedCheckNodeConstant::new));
  }

  public static Parser parse(Parser input, Consumer<DefinedCheckNode> output) {
    final AtomicReference<Function<String, DefinedCheckNode>> check = new AtomicReference<>();
    final AtomicReference<String> name = new AtomicReference<>();

    final Parser result =
        input
            .whitespace()
            .dispatch(CHECK, check::set)
            .whitespace()
            .qualifiedIdentifier(name::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(check.get().apply(name.get()));
    }

    return result;
  }

  public abstract boolean check(ExpressionCompilerServices expressionCompilerServices);

  public abstract boolean check(NameDefinitions defs);
}
