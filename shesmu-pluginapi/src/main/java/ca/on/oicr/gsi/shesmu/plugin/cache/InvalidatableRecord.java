package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Caches a record until it self-identifies as stale */
public class InvalidatableRecord<V> implements Record<Optional<V>> {
  /**
   * Build a new cache record type that checks when a record is valid and does a clean up when the
   * item is being replace
   *
   * @param isValid the predicate to check if the cached value is still valid
   * @param destructor the clean up procedure
   */
  public static <V> RecordFactory<Optional<V>, Optional<V>> checking(
      Predicate<? super V> isValid, Consumer<? super V> destructor) {
    return (fetcher) -> new InvalidatableRecord<>(fetcher, isValid, destructor);
  }

  private final Consumer<? super V> destructor;
  private final Updater<Optional<V>> fetcher;
  private boolean initialState = true;
  private final Predicate<? super V> isValid;
  private Instant lastUpdated = Instant.EPOCH;
  private Optional<V> value = Optional.empty();

  public InvalidatableRecord(
      Updater<Optional<V>> fetcher, Predicate<? super V> isValid, Consumer<? super V> destructor) {
    super();
    this.fetcher = fetcher;
    this.isValid = isValid;
    this.destructor = destructor;
  }

  @Override
  public int collectionSize() {
    return value.isPresent() ? 1 : 0;
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
  public synchronized Optional<V> readStale() {
    return value;
  }

  @Override
  public synchronized Optional<V> refresh(String context) {
    value = value.filter(isValid);
    if (value.isPresent()) {
      return value;
    }
    try (var timer = refreshLatency.start(fetcher.owner().name())) {
      value = fetcher.update(lastUpdated);
      if (value.isPresent()) {
        lastUpdated = Instant.now();
        initialState = false;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (initialState) {
      throw new InitialCachePopulationException(fetcher.owner().name());
    }
    return value;
  }

  @Override
  public Updater<?> updater() {
    return fetcher;
  }
}
