package ca.on.oicr.gsi.shesmu.plugin.files;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Creates a repository of updating files stored in a directory
 *
 * @param <T> the wrapper class for updating each file
 */
public final class AutoUpdatingDirectory<T extends WatchedFileListener> {

  private final Map<Path, T> active = new ConcurrentHashMap<>();
  private final AtomicInteger idGen = new AtomicInteger();
  private final Map<Integer, Runnable> subscribers = new ConcurrentHashMap<>();

  /**
   * Creates a new automatically updating directory
   *
   * @param watcher the file watcher to use
   * @param extension the file extension for the files that should be loaded
   * @param ctor a constructor to create a new self-updating file for a path
   */
  public AutoUpdatingDirectory(FileWatcher watcher, String extension, Function<Path, T> ctor) {
    super();
    watcher.register(
        extension,
        path -> {
          final var inner = ctor.apply(path);
          return new WatchedFileListener() {

            @Override
            public void start() {
              inner.start();
              active.put(path, inner);
              subscribers.values().forEach(Runnable::run);
            }

            @Override
            public void stop() {
              active.remove(path, inner);
              inner.stop();
              subscribers.values().forEach(Runnable::run);
            }

            @Override
            public Optional<Integer> update() {
              final var delay = inner.update();
              subscribers.values().forEach(Runnable::run);
              return delay;
            }
          };
        });
  }

  /**
   * Creates a new automatically updating directory
   *
   * @param extension the file extension for the files that should be loaded
   * @param ctor a constructor to create a new self-updating file for a path
   */
  public AutoUpdatingDirectory(String extension, Function<Path, T> ctor) {
    this(FileWatcher.DATA_DIRECTORY, extension, ctor);
  }

  public boolean isEmpty() {
    return active.isEmpty();
  }

  public Stream<T> stream() {
    return active.values().stream();
  }

  public Runnable subscribe(Runnable callback) {
    final var id = idGen.getAndIncrement();
    subscribers.put(id, callback);
    return () -> subscribers.remove(id);
  }
}
