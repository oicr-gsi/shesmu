package ca.on.oicr.gsi.shesmu.core.groupers;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class AlwaysIncludeGrouper<I, O, T> implements Grouper<I, O> {
  private final BiFunction<T, Function<I, Boolean>, BiConsumer<O, I>> collectorForKey;
  private final Function<I, T> computeKey;
  private final T wildcardValue;

  public AlwaysIncludeGrouper(
      Function<I, T> computeKey,
      T wildcardValue,
      BiFunction<T, Function<I, Boolean>, BiConsumer<O, I>> collectorForKey) {
    this.collectorForKey = collectorForKey;
    this.computeKey = computeKey;
    this.wildcardValue = wildcardValue;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    final Map<T, List<I>> groups = new HashMap<>();
    final List<I> wildcards = new ArrayList<>();
    for (var input : inputs) {
      final var key = computeKey.apply(input);
      if (key.equals(wildcardValue)) {
        wildcards.add(input);
      } else {
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(input);
      }
    }
    return groups.entrySet().stream()
        .map(
            entry ->
                new Subgroup<>(
                    collectorForKey.apply(entry.getKey(), wildcards::contains),
                    Stream.concat(wildcards.stream(), entry.getValue().stream())));
  }
}
