package ca.on.oicr.gsi.shesmu.plugin.files;

import io.prometheus.client.Gauge;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Wrapper around Java's file watching to provide a better interface */
public abstract class FileWatcher {
  private static final class RealFileWatcher extends FileWatcher {
    private static final class RetryProcess {
      private final WatchedFileListener listener;
      private final Path path;
      private final Instant retryTime;

      private RetryProcess(Instant retryTime, WatchedFileListener listener, Path path) {
        this.retryTime = retryTime;
        this.listener = listener;
        this.path = path;
      }

      public String filename() {
        return path.toString();
      }

      public Instant time() {
        return retryTime;
      }

      public void update(Consumer<RetryProcess> next, Instant now) {
        listener
            .update()
            .ifPresent(
                retryTimeout ->
                    next.accept(
                        new RetryProcess(
                            now.plus(retryTimeout, ChronoUnit.MINUTES), listener, path)));
      }
    }

    private final Map<Path, List<WatchedFileListener>> active = new ConcurrentHashMap<>();
    private final Map<String, List<Function<Path, WatchedFileListener>>> ctors =
        new ConcurrentHashMap<>();
    private final List<Path> directories;
    private final PriorityBlockingQueue<RetryProcess> retry =
        new PriorityBlockingQueue<>(200, Comparator.comparing(RetryProcess::time));
    private volatile boolean running = true;
    private final Thread watchThread = new Thread(this::run, "file-watcher");

    private RealFileWatcher(Stream<String> directory) {
      directories =
          directory
              .map(Paths::get)
              .map(
                  t -> {
                    try {
                      return t.toRealPath();
                    } catch (final IOException e) {
                      e.printStackTrace();
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      watchThread.start();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {

                @Override
                public void run() {
                  running = false;
                  try {
                    watchThread.interrupt();
                    watchThread.join();
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }
              });
    }

    @Override
    public Stream<Path> paths() {
      return directories.stream();
    }

    @Override
    public synchronized void register(String extension, Function<Path, WatchedFileListener> ctor) {
      List<Function<Path, WatchedFileListener>> holder;
      if (!ctors.containsKey(extension)) {
        holder = Collections.synchronizedList(new ArrayList<>());
        ctors.put(extension, holder);
      } else {
        holder = ctors.get(extension);
      }
      holder.add(ctor);
      for (final Path directory : directories) {
        try (Stream<Path> stream = Files.walk(directory, 1)) {
          stream
              .filter(
                  path -> {
                    final String fileName = path.getFileName().toString();
                    return fileName.endsWith(extension) && !fileName.startsWith(".");
                  })
              .forEach(
                  path -> {
                    final WatchedFileListener file = ctor.apply(path);
                    List<WatchedFileListener> fileHolder;
                    if (active.containsKey(path)) {
                      fileHolder = active.get(path);
                    } else {
                      fileHolder = Collections.synchronizedList(new ArrayList<>());
                      active.put(path, fileHolder);
                    }
                    fileHolder.add(file);
                    updateTime.labels(path.toString()).setToCurrentTime();
                    start(file, path);
                  });
        } catch (final IOException e) {
          e.printStackTrace();
        }
      }
    }

    private void run() {
      try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
        for (final Path directory : directories) {
          directory.register(
              watchService,
              StandardWatchEventKinds.ENTRY_MODIFY,
              StandardWatchEventKinds.ENTRY_CREATE,
              StandardWatchEventKinds.ENTRY_DELETE);
        }
        while (running) {
          try {
            final Instant now = Instant.now();
            final Optional<Long> timeout =
                Optional.ofNullable(retry.peek())
                    .map(p -> Duration.between(now, p.time()).toMillis());
            final WatchKey wk =
                timeout.isPresent()
                    ? timeout.get() < 0
                        ? null
                        : watchService.poll(timeout.get(), TimeUnit.MILLISECONDS)
                    : watchService.take();
            if (wk == null) {
              System.out.println("Timeout occurred. Reloading stale files.");
              final List<RetryProcess> retryOutput = new ArrayList<>();
              while (true) {
                final RetryProcess current = retry.poll();
                if (current == null) break;
                if (Duration.between(now, current.time()).toMillis() <= 0) {
                  try (AutoCloseable timer =
                      fileUpdateDuration.labels(current.filename()).startTimer()) {
                    current.update(retryOutput::add, now);
                    ;
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                } else {
                  retryOutput.add(current);
                  break;
                }
              }
              retry.addAll(retryOutput);
            } else {
              for (final WatchEvent<?> event : wk.pollEvents()) {
                final Path path = ((Path) wk.watchable()).resolve((Path) event.context());
                if (path.getFileName().toString().startsWith(".")) {
                  continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                  System.out.printf("New file %s detected.\n", path.toString());
                  final String fileName = path.getFileName().toString();
                  active.put(
                      path,
                      ctors
                          .entrySet()
                          .stream()
                          .filter(entry -> fileName.endsWith(entry.getKey()))
                          .flatMap(entry -> entry.getValue().stream())
                          .map(ctor -> ctor.apply(path))
                          .peek(listener -> start(listener, path))
                          .collect(Collectors.toList()));
                  updateTime.labels(path.toString()).setToCurrentTime();
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                  System.out.printf("File %s deleted.\n", path.toString());
                  final List<WatchedFileListener> listeners = active.remove(path);
                  if (listeners != null) {
                    retry.removeIf(e -> listeners.contains(e.listener));
                    listeners.forEach(WatchedFileListener::stop);
                    updateTime.labels(path.toString()).setToCurrentTime();
                  }
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                  System.out.printf("File %s updated.\n", path.toString());
                  final List<WatchedFileListener> listeners = active.get(path);
                  if (listeners != null) {
                    retry.removeIf(e -> listeners.contains(e.listener));
                    listeners
                        .stream()
                        .<Optional<RetryProcess>>map(
                            listener -> {
                              try (AutoCloseable timer =
                                  fileUpdateDuration.labels(path.toString()).startTimer()) {
                                return listener
                                    .update()
                                    .map(
                                        retryTimeout ->
                                            new RetryProcess(
                                                now.plus(retryTimeout, ChronoUnit.MINUTES),
                                                listener,
                                                path));
                              } catch (Exception e) {
                                e.printStackTrace();
                                return Optional.empty();
                              }
                            })
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(retry::add);
                    updateTime.labels(path.toString()).setToCurrentTime();
                  }
                }
              }
              wk.reset();
            }
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }

    private void start(WatchedFileListener file, Path path) {
      try {
        file.start();
        file.update()
            .ifPresent(
                timeout ->
                    retry.offer(
                        new RetryProcess(
                            Instant.now().plus(timeout, ChronoUnit.MINUTES), file, path)));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static final FileWatcher DATA_DIRECTORY =
      Optional.ofNullable(System.getenv("SHESMU_DATA"))
          .map(Pattern.compile(Pattern.quote(File.pathSeparator))::splitAsStream)
          .<FileWatcher>map(RealFileWatcher::new)
          .orElseGet(
              () ->
                  new FileWatcher() {

                    @Override
                    public Stream<Path> paths() {
                      return Stream.empty();
                    }

                    @Override
                    public void register(
                        String extension, Function<Path, WatchedFileListener> ctor) {
                      // Do nothing
                    }
                  });
  private static final Gauge fileUpdateDuration =
      Gauge.build("shesmu_file_update_duration", "Number of seconds to update a Shesmu file")
          .labelNames("filename")
          .register();
  private static final Gauge updateTime =
      Gauge.build(
              "shesmu_auto_update_timestamp",
              "The UNIX time when a file or directory was last updated.")
          .labelNames("filename")
          .register();

  public abstract Stream<Path> paths();

  public abstract void register(String extension, Function<Path, WatchedFileListener> ctor);
}
