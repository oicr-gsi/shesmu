package ca.on.oicr.gsi.shesmu.plugin.files;

import ca.on.oicr.gsi.Pair;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Wrapper around Java's file watching to provide a better interface */
public abstract class FileWatcher {
  private static final class RealFileWatcher extends FileWatcher {
    private final Map<Path, List<WatchedFileListener>> active = new ConcurrentHashMap<>();
    private final Map<String, List<Function<Path, WatchedFileListener>>> ctors =
        new ConcurrentHashMap<>();
    private final List<Path> directories;
    private final PriorityBlockingQueue<Pair<Instant, WatchedFileListener>> retry =
        new PriorityBlockingQueue<>(200, Comparator.comparing(Pair::first));
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
                    start(file);
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
                    .map(p -> Duration.between(now, p.first()).toMillis());
            final WatchKey wk =
                timeout.isPresent()
                    ? timeout.get() < 0
                        ? null
                        : watchService.poll(timeout.get(), TimeUnit.MILLISECONDS)
                    : watchService.take();
            if (wk == null) {
              System.out.println("Timeout occurred. Reloading stale files.");
              final List<Pair<Instant, WatchedFileListener>> retryOutput = new ArrayList<>();
              while (true) {
                final Pair<Instant, WatchedFileListener> current = retry.poll();
                if (current == null) break;
                if (Duration.between(now, current.first()).toMillis() <= 0) {
                  current
                      .second()
                      .update()
                      .ifPresent(
                          retryTimeout ->
                              retryOutput.add(
                                  new Pair<>(
                                      now.plus(retryTimeout, ChronoUnit.MINUTES),
                                      current.second())));
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
                          .peek(this::start)
                          .collect(Collectors.toList()));
                  updateTime.labels(path.toString()).setToCurrentTime();
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                  System.out.printf("File %s deleted.\n", path.toString());
                  final List<WatchedFileListener> listeners = active.remove(path);
                  if (listeners != null) {
                    listeners.forEach(WatchedFileListener::stop);
                    updateTime.labels(path.toString()).setToCurrentTime();
                  }
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                  System.out.printf("File %s updated.\n", path.toString());
                  final List<WatchedFileListener> listeners = active.get(path);
                  if (listeners != null) {
                    listeners
                        .stream()
                        .map(
                            listener ->
                                listener
                                    .update()
                                    .map(
                                        retryTimeout ->
                                            new Pair<>(
                                                now.plus(retryTimeout, ChronoUnit.MINUTES),
                                                listener)))
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

    private void start(WatchedFileListener file) {
      file.start();
      file.update()
          .ifPresent(
              timeout ->
                  retry.offer(new Pair<>(Instant.now().plus(timeout, ChronoUnit.MINUTES), file)));
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
  private static final Gauge updateTime =
      Gauge.build(
              "shesmu_auto_update_timestamp",
              "The UNIX time when a file or directory was last updated.")
          .labelNames("filename")
          .register();

  public abstract Stream<Path> paths();

  public abstract void register(String extension, Function<Path, WatchedFileListener> ctor);
}
