package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class PickNode {
  static Parser parse(Parser parser, Consumer<PickNode> output) {
    final var group = new AtomicBoolean(false);
    final var name = new AtomicReference<String>();
    final var result =
        parser
            .whitespace()
            .regex(ATSIGN, m -> group.set(!m.group().isEmpty()), "“@” for group, or nothing")
            .whitespace()
            .identifier(name::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          group.get()
              ? new PickNodeGang(parser.line(), parser.column(), name.get())
              : new PickNodeSimple(name.get()));
    }
    return result;
  }

  private static final Pattern ATSIGN = Pattern.compile("@?");

  public abstract boolean isGood(InputFormatDefinition inputFormat, Consumer<String> errorHandler);

  public abstract Stream<String> names();
}
