package ca.on.oicr.gsi.shesmu.runtime;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class UnivaluedCollector<T>
    implements Collector<T, UnivaluedCollector.Info<T>, Optional<T>> {
  enum State {
    EMPTY,
    BAD,
    GOOD
  }

  public static class Info<T> {
    State state = State.EMPTY;
    T value;

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

    public Optional<T> finish() {
      return state == State.GOOD ? Optional.of(value) : Optional.empty();
    }
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
  public Function<Info<T>, Optional<T>> finisher() {
    return Info::finish;
  }

  @Override
  public Supplier<Info<T>> supplier() {
    return Info::new;
  }
}
