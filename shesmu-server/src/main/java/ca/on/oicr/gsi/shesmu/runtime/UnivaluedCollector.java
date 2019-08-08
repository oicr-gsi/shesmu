package ca.on.oicr.gsi.shesmu.runtime;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class UnivaluedCollector<T> implements Collector<T, UnivaluedCollector.Info<T>, T> {
  enum State {
    EMPTY,
    BAD,
    GOOD
  }

  public static class Info<T> {
    private final Supplier<T> defaultValue;
    State state = State.EMPTY;
    T value;

    public Info(Supplier<T> defaultValue) {
      this.defaultValue = defaultValue;
    }

    void accumulate(T value) {
      if (state == State.EMPTY) {
        this.value = value;
        this.state = State.GOOD;
      } else if (state == State.GOOD && !this.value.equals(value)) {
        this.state = State.BAD;
      }
    }

    public Info<T> combine(Info<T> other) {
      if (other.state == State.BAD) {
        return other;
      }
      if (other.state == State.GOOD) {
        accumulate(other.value);
      }
      return this;
    }

    public T finish() {
      return state == State.GOOD ? value : defaultValue.get();
    }
  }

  private final Supplier<T> defaultValue;

  public UnivaluedCollector(Supplier<T> defaultValue) {
    this.defaultValue = defaultValue;
  }

  @Override
  public BiConsumer<Info<T>, T> accumulator() {
    return Info::accumulate;
  }

  @Override
  public Set<Characteristics> characteristics() {
    return EnumSet.of(Characteristics.UNORDERED);
  }

  @Override
  public BinaryOperator<Info<T>> combiner() {
    return Info::combine;
  }

  @Override
  public Function<Info<T>, T> finisher() {
    return Info::finish;
  }

  @Override
  public Supplier<Info<T>> supplier() {
    return () -> new Info<>(defaultValue);
  }
}
