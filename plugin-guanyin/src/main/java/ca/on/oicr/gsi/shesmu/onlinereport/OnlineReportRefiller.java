package ca.on.oicr.gsi.shesmu.onlinereport;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.refill.RefillerParameter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class OnlineReportRefiller<T> extends Refiller<T> {
  @RefillerParameter public Function<T, String> label;
  private final Supplier<OnlineReport> owner;
  final List<BiConsumer<T, ObjectNode>> writers = new ArrayList<>();

  public OnlineReportRefiller(Supplier<OnlineReport> owner) {
    this.owner = owner;
  }

  @Override
  public void consume(Stream<T> items) {
    owner
        .get()
        .processInput(
            items.map(
                i -> {
                  final var node = OnlineReport.MAPPER.createObjectNode();
                  for (final var writer : writers) {
                    writer.accept(i, node);
                  }
                  return new Pair<>(label.apply(i), node);
                }));
  }
}
