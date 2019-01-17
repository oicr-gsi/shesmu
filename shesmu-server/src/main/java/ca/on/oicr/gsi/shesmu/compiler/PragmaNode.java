package ca.on.oicr.gsi.shesmu.compiler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public abstract class PragmaNode {
  private static final Parser.ParseDispatch<PragmaNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Timeout",
        (p, o) -> {
          final AtomicLong timeout = new AtomicLong();
          final AtomicInteger multiplier = new AtomicInteger();
          final Parser result =
              p.whitespace()
                  .integer(timeout::set, 10)
                  .dispatch(ExpressionNode.INT_SUFFIX, multiplier::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new PragmaNodeTimeout((int) (timeout.get() * multiplier.get())));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<PragmaNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public abstract void renderGuard(RootBuilder root);

  public abstract void timeout(AtomicInteger timeout);
}
