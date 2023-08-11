package ca.on.oicr.gsi.shesmu.plugin;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * A Stream with a boolean to indicate whether an error occurred surrounding Stream population
 * circumstances. Error setting is strictly the users problem - nothing within this class should set
 * OK to false without being passed that information.
 *
 * <p>of() and ofNullable() don't make sense to reimplement - create a new ErrorableStream with an
 * `inner` of Stream.of(whatever)
 *
 * <p>Stream functions involving IntStream etc just aren't supported right now.
 *
 * @param <T> Stream elements type
 */
public class ErrorableStream<T> implements Stream<T> {
  private final Stream<T> inner;
  private boolean ok;

  public ErrorableStream(Stream<T> inner, boolean ok) {
    this.inner = inner;
    this.ok = ok;
  }

  /**
   * Create a new ErrorableStream from any Stream. In the case that inner is an ErrorableStream
   * itself, preserve its OKness. It can be easier to convert a mix of Errorable- and other types of
   * Stream all to ErrorableStream rather than trying to test and cast at the time of use if an
   * interface only promises some Stream. Since other Streams have no concept of 'OK', they are OK
   * by default.
   *
   * @param inner Stream to clone
   */
  public ErrorableStream(Stream<T> inner) {
    this.inner = inner;
    if (inner instanceof ErrorableStream<T>) {
      this.ok = ((ErrorableStream<T>) inner).ok;
    } else {
      this.ok = true;
    }
  }

  public void invalidate() {
    this.ok = false;
  }

  public boolean isOk() {
    return ok;
  }

  @Override
  public Stream<T> filter(Predicate<? super T> predicate) {
    return new ErrorableStream<>(inner.filter(predicate), this.ok);
  }

  @Override
  public <R> Stream<R> map(Function<? super T, ? extends R> function) {
    return new ErrorableStream<>(inner.map(function), this.ok);
  }

  @Override
  public IntStream mapToInt(ToIntFunction<? super T> toIntFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LongStream mapToLong(ToLongFunction<? super T> toLongFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DoubleStream mapToDouble(ToDoubleFunction<? super T> toDoubleFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> function) {
    Stream.Builder<R> builder = Stream.builder();
    inner.forEachOrdered(
        s -> {
          Stream<? extends R> resultStream = function.apply(s);
          resultStream.forEach(builder::accept);
          if (s instanceof ErrorableStream<?> es) this.ok &= es.ok;
          if (resultStream instanceof ErrorableStream<? extends R> res) this.ok &= res.ok;
        });

    return new ErrorableStream<>(builder.build(), this.ok);
  }

  @Override
  public IntStream flatMapToInt(Function<? super T, ? extends IntStream> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LongStream flatMapToLong(Function<? super T, ? extends LongStream> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<T> distinct() {
    return new ErrorableStream<>(inner.distinct(), this.ok);
  }

  @Override
  public Stream<T> sorted() {
    return new ErrorableStream<>(inner.sorted(), this.ok);
  }

  @Override
  public Stream<T> sorted(Comparator<? super T> comparator) {
    return new ErrorableStream<>(inner.sorted(comparator), this.ok);
  }

  @Override
  public Stream<T> peek(Consumer<? super T> consumer) {
    return new ErrorableStream<>(inner.peek(consumer), this.ok);
  }

  @Override
  public Stream<T> limit(long l) {
    return new ErrorableStream<>(inner.limit(l), this.ok);
  }

  @Override
  public Stream<T> skip(long l) {
    return new ErrorableStream<>(inner.skip(l), this.ok);
  }

  @Override
  public void forEach(Consumer<? super T> consumer) {
    inner.forEach(consumer);
  }

  @Override
  public void forEachOrdered(Consumer<? super T> consumer) {
    inner.forEachOrdered(consumer);
  }

  @Override
  public Object[] toArray() {
    return inner.toArray();
  }

  @Override
  public <A> A[] toArray(IntFunction<A[]> intFunction) {
    return inner.toArray(intFunction);
  }

  @Override
  public T reduce(T t, BinaryOperator<T> binaryOperator) {
    return inner.reduce(t, binaryOperator);
  }

  @Override
  public Optional<T> reduce(BinaryOperator<T> binaryOperator) {
    return inner.reduce(binaryOperator);
  }

  @Override
  public <U> U reduce(
      U u, BiFunction<U, ? super T, U> biFunction, BinaryOperator<U> binaryOperator) {
    return inner.reduce(u, biFunction, binaryOperator);
  }

  @Override
  public <R> R collect(
      Supplier<R> supplier, BiConsumer<R, ? super T> biConsumer, BiConsumer<R, R> biConsumer1) {
    return inner.collect(supplier, biConsumer, biConsumer1);
  }

  @Override
  public <R, A> R collect(Collector<? super T, A, R> collector) {
    return inner.collect(collector);
  }

  @Override
  public Optional<T> min(Comparator<? super T> comparator) {
    return inner.min(comparator);
  }

  @Override
  public Optional<T> max(Comparator<? super T> comparator) {
    return inner.max(comparator);
  }

  @Override
  public long count() {
    return inner.count();
  }

  @Override
  public boolean anyMatch(Predicate<? super T> predicate) {
    return inner.anyMatch(predicate);
  }

  @Override
  public boolean allMatch(Predicate<? super T> predicate) {
    return inner.allMatch(predicate);
  }

  @Override
  public boolean noneMatch(Predicate<? super T> predicate) {
    return inner.noneMatch(predicate);
  }

  @Override
  public Optional<T> findFirst() {
    return inner.findFirst();
  }

  @Override
  public Optional<T> findAny() {
    return inner.findAny();
  }

  @Override
  public Iterator<T> iterator() {
    return inner.iterator();
  }

  @Override
  public Spliterator<T> spliterator() {
    return inner.spliterator();
  }

  @Override
  public boolean isParallel() {
    return inner.isParallel();
  }

  @Override
  public Stream<T> sequential() {
    return new ErrorableStream<>(inner.sequential(), this.ok);
  }

  @Override
  public Stream<T> parallel() {
    return new ErrorableStream<>(inner.parallel(), this.ok);
  }

  @Override
  public Stream<T> unordered() {
    return new ErrorableStream<>(inner.unordered(), this.ok);
  }

  @Override
  public Stream<T> onClose(Runnable runnable) {
    return new ErrorableStream<>(inner.onClose(runnable), this.ok);
  }

  @Override
  public void close() {
    inner.close();
  }

  @Override
  public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
    return new ErrorableStream<>(inner.mapMulti(mapper), this.ok);
  }

  @Override
  public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> mapper) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> mapper) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> mapper) {
    throw new UnsupportedOperationException();
  }

  public static <T> ErrorableStream<T> concatWithErrors(
      Stream<? extends T> a, Stream<? extends T> b) {

    if ((a instanceof ErrorableStream<? extends T>)
        && (b instanceof ErrorableStream<? extends T>)) {
      return new ErrorableStream<>(
          Stream.concat(
              ((ErrorableStream<? extends T>) a).inner, ((ErrorableStream<? extends T>) b).inner),
          ((ErrorableStream<? extends T>) a).ok && ((ErrorableStream<? extends T>) b).ok);
    } else if (a instanceof ErrorableStream<? extends T>) {
      return new ErrorableStream<>(
          Stream.concat(((ErrorableStream<? extends T>) a).inner, b),
          ((ErrorableStream<? extends T>) a).ok);
    } else if (b instanceof ErrorableStream<? extends T>) {
      return new ErrorableStream<>(
          Stream.concat(a, ((ErrorableStream<? extends T>) b).inner),
          ((ErrorableStream<? extends T>) b).ok);
    } else { // a and b are both not ErrorableStream
      return new ErrorableStream<>(Stream.concat(a, b), true);
    }
  }
}
