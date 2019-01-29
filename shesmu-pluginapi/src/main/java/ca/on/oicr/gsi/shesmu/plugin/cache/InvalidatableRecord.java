package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Caches a record until it self-identifies as stale */
public class InvalidatableRecord<V> implements Record<Optional<V>> {
  private final Predicate<? super V> isValid;
  private final Updater<Optional<V>> fetcher;
  private Optional<V> value = Optional.empty();
  private Instant lastUpdated = Instant.EPOCH;
  private final Consumer<? super V> destructor;
  private final Owner owner;

  /**
   * Build a new cache record type that checks when a record is valid and does a clean up when the
   * item is being replace
   *
   * @param isValid the predicate to check if the cached value is still valid
   * @param destructor the clean up procedure
   */
  public static <V> BiFunction<Owner, Updater<Optional<V>>, Record<Optional<V>>> checking(
      Predicate<? super V> isValid, Consumer<? super V> destructor) {
    return (owner, fetcher) -> new InvalidatableRecord<V>(owner, fetcher, isValid, destructor);
  }

  public InvalidatableRecord(
      Owner owner,
      Updater<Optional<V>> fetcher,
      Predicate<? super V> isValid,
      Consumer<? super V> destructor) {
    super();
    this.owner = owner;
    this.fetcher = fetcher;
    this.isValid = isValid;
    this.destructor = destructor;
  }

  @Override
  public void invalidate() {
    value.ifPresent(destructor);
    value = Optional.empty();
  }

  @Override
  public Instant lastUpdate() {
    return lastUpdated;
  }

  @Override
  public synchronized Optional<V> refresh() {
    value = value.filter(isValid);
    if (value.isPresent()) {
      return value;
    }
    try (AutoCloseable timer = refreshLatency.start(owner.name())) {
      value = fetcher.update(lastUpdated);
      if (value.isPresent()) {
        lastUpdated = Instant.now();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return value;
  }

  @Override
  public int collectionSize() {
    return value.isPresent() ? 1 : 0;
  }
}
