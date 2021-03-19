package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class PragmaNode {
  private static final Parser.ParseDispatch<PragmaNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Timeout",
        (p, o) -> {
          final var timeout = new AtomicLong();
          final var multiplier = new AtomicInteger();
          final var result =
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

    DISPATCH.addKeyword(
        "RequiredServices",
        (input, output) -> {
          final var services = new AtomicReference<List<String>>(List.of());
          final var result =
              input
                  .whitespace()
                  .list(services::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            output.accept(new PragmaNodeRequiredServices(services.get()));
          }

          return result;
        });

    DISPATCH.addKeyword(
        "Frequency",
        (p, o) -> {
          final var frequency = new AtomicLong();
          final var multiplier = new AtomicInteger();
          final var result =
              p.whitespace()
                  .integer(frequency::set, 10)
                  .dispatch(ExpressionNode.INT_SUFFIX, multiplier::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new PragmaNodeFrequency((int) (frequency.get() * multiplier.get())));
          }
          return result;
        });

    DISPATCH.addKeyword(
        "Import",
        (p, o) -> {
          final var namespaces = new AtomicReference<List<String>>();
          final var import_ = new AtomicReference<List<ImportNode>>();
          final var result =
              p.whitespace()
                  .list(
                      namespaces::set,
                      (partParser, partOutput) -> {
                        final var part = new AtomicReference<String>();
                        final var partResult =
                            partParser
                                .whitespace()
                                .identifier(part::set)
                                .whitespace()
                                .symbol(Parser.NAMESPACE_SEPARATOR)
                                .whitespace();
                        if (partResult.isGood()) {
                          partOutput.accept(part.get());
                        }
                        return partResult;
                      })
                  .then(ImportNode::parse, import_::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new PragmaNodeImport(
                    String.join(Parser.NAMESPACE_SEPARATOR, namespaces.get()), import_.get()));
          }
          return result;
        });

    DISPATCH.addKeyword(
        "Check",
        (p, o) -> {
          final var format = new AtomicReference<String>();
          final var check = new AtomicReference<ExpressionNode>();
          final var collect = new AtomicReference<GroupNode>();
          final var result =
              p.whitespace()
                  .identifier(format::set)
                  .whitespace()
                  .keyword("Into")
                  .whitespace()
                  .then(GroupNode::parse, collect::set)
                  .whitespace()
                  .keyword("Require")
                  .whitespace()
                  .then(ExpressionNode::parse, check::set)
                  .whitespace()
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new PragmaNodeInputGuard(
                    p.line(), p.column(), format.get(), collect.get(), check.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<PragmaNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public boolean check(OliveCompilerServices services, Consumer<String> errorHandler) {
    return true;
  }

  public abstract Stream<ImportRewriter> imports();

  public abstract void renderAtExit(RootBuilder root);

  public abstract void renderGuard(RootBuilder root);

  public abstract void timeout(AtomicInteger timeout);
}
