package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class CombinationsGrouper<I, O> implements Grouper<I, O> {

  private final int groupSize;
  private final Supplier<BiConsumer<O, I>> collectorConstructor;

  public CombinationsGrouper(long groupSize, Supplier<BiConsumer<O, I>> collectorConstructor) {
    this.groupSize = (int) groupSize;
    this.collectorConstructor = collectorConstructor;
  }

  private List<int[]> generate(int total) {
    // https://www.baeldung.com/java-combinations-algorithm
    final List<int[]> combinations = new ArrayList<>();
    int[] combination = new int[groupSize];

    // initialize with lowest lexicographic combination
    for (int i = 0; i < groupSize; i++) {
      combination[i] = i;
    }

    while (combination[groupSize - 1] < total) {
      combinations.add(combination.clone());

      // generate next combination in lexicographic order
      int t = groupSize - 1;
      while (t != 0 && combination[t] == total - groupSize + t) {
        t--;
      }
      combination[t]++;
      for (int i = t + 1; i < groupSize; i++) {
        combination[i] = combination[i - 1] + 1;
      }
    }

    return combinations;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    if (inputs.size() < groupSize) {
      return Stream.empty();
    }
    if (inputs.size() == groupSize) {
      return Stream.of(new Subgroup<>(collectorConstructor.get(), inputs.stream()));
    }
    return generate(inputs.size())
        .stream()
        .map(
            combinations ->
                new Subgroup<>(
                    collectorConstructor.get(), IntStream.of(combinations).mapToObj(inputs::get)));
  }
}
