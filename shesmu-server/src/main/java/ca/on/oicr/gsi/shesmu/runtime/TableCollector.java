package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class TableCollector<T> implements Collector<T, List<String>, String> {
  private final List<Pair<String, Function<T, String>>> columns;
  private final String dataEnd;
  private final String dataSeparator;
  private final String dataStart;
  private final Optional<String> headerUnderline;
  private final String headerEnd;
  private final String headerSeparator;
  private final String headerStart;

  @SafeVarargs
  @RuntimeInterop
  public TableCollector(Tuple separators, Pair<String, Function<T, String>>... columns) {
    this.columns = List.of(columns);
    this.dataEnd = (String) separators.get(0);
    this.dataSeparator = (String) separators.get(1);
    this.dataStart = (String) separators.get(2);
    this.headerEnd = (String) separators.get(3);
    this.headerSeparator = (String) separators.get(4);
    this.headerStart = (String) separators.get(5);
    this.headerUnderline = ((Optional<?>) separators.get(6)).map(String.class::cast);
  }

  @Override
  public Supplier<List<String>> supplier() {
    return ArrayList::new;
  }

  @Override
  public BiConsumer<List<String>, T> accumulator() {
    return (list, item) ->
        list.add(
            columns.stream()
                .map(c -> c.second().apply(item))
                .collect(Collectors.joining(dataSeparator, dataStart, dataEnd)));
  }

  @Override
  public BinaryOperator<List<String>> combiner() {
    return (a, b) -> {
      a.addAll(b);
      return a;
    };
  }

  @Override
  public Function<List<String>, String> finisher() {
    return l ->
        columns.stream()
                .map(Pair::first)
                .collect(Collectors.joining(headerSeparator, headerStart, headerEnd))
            + "\n"
            + headerUnderline
                .map(u -> String.join("", Collections.nCopies(columns.size(), u)) + "\n")
                .orElse("")
            + String.join("\n", l);
  }

  @Override
  public Set<Characteristics> characteristics() {
    return EnumSet.noneOf(Characteristics.class);
  }
}
