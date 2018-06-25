package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.prometheus.client.Gauge;

public abstract class FileWatcher {
	private static final Gauge updateTime = Gauge
			.build("shesmu_auto_update_timestamp", "The UNIX time when a file or directory was last updated.")
			.labelNames("filename").register();

	public static final FileWatcher DATA_DIRECTORY = RuntimeSupport.dataDirectory()
			.<FileWatcher>map(directory -> new FileWatcher() {
				private final Map<Path, List<WatchedFileListener>> active = new ConcurrentHashMap<>();

				private final Map<String, List<Function<Path, WatchedFileListener>>> ctors = new ConcurrentHashMap<>();
				private volatile boolean running = true;

				private final Thread watchThread = new Thread(this::run, "file-watcher");

				{
					watchThread.start();
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
					try (Stream<Path> stream = Files.walk(directory, 1)) {
						stream.filter(path -> path.getFileName().toString().endsWith(extension))//
								.forEach(path -> {
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
									file.start();
								});
					} catch (final IOException e) {
						e.printStackTrace();
					}

				}

				private void run() {
					try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
						directory.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
								StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
						while (running) {
							try {
								List<Pair<Instant, WatchedFileListener>> retry = new ArrayList<>();
								final Instant now = Instant.now();
								final OptionalLong timeout = retry.stream()
										.mapToLong(p -> Duration.between(p.first(), now).toMillis()).min();
								final WatchKey wk = timeout.isPresent()
										? timeout.getAsLong() < 0 ? null
												: watchService.poll(timeout.getAsLong(), TimeUnit.MILLISECONDS)
										: watchService.take();
								if (wk == null) {
									retry = Stream.concat(//
											retry.stream()//
													.filter(p -> Duration.between(p.first(), now).toMillis() > 0), //
											retry.stream()//
													.filter(p -> Duration.between(p.first(), now).toMillis() <= 0)//
													.<Optional<Pair<Instant, WatchedFileListener>>>map(
															listener -> listener.second().update()
																	.map(retryTimeout -> new Pair<>(
																			now.plus(retryTimeout, ChronoUnit.MINUTES),
																			listener.second())))//
													.filter(Optional::isPresent)//
													.map(Optional::get))
											.collect(Collectors.toList());
								} else {
									for (final WatchEvent<?> event : wk.pollEvents()) {
										final Path path = directory.resolve((Path) event.context());
										if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
											final String fileName = path.getFileName().toString();
											active.put(path, ctors.entrySet().stream()//
													.filter(entry -> fileName.endsWith(entry.getKey()))//
													.flatMap(entry -> entry.getValue().stream())//
													.map(ctor -> ctor.apply(path)).collect(Collectors.toList()));
											updateTime.labels(path.toString()).setToCurrentTime();
										} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
											final List<WatchedFileListener> listeners = active.remove(path);
											if (listeners != null) {
												listeners.forEach(WatchedFileListener::stop);
												updateTime.labels(path.toString()).setToCurrentTime();
											}
										} else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
											final List<WatchedFileListener> listeners = active.get(path);
											if (listeners != null) {
												listeners.stream()//
														.map(listener -> listener.update()//
																.map(retryTimeout -> new Pair<>(
																		now.plus(retryTimeout, ChronoUnit.MINUTES),
																		listener)))//
														.filter(Optional::isPresent)//
														.map(Optional::get)//
														.forEach(retry::add);
												updateTime.labels(path.toString()).setToCurrentTime();
											}
										}

									}
									wk.reset();
								}
							} catch (final InterruptedException e) {
							}
						}
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}

			}).orElseGet(() -> new FileWatcher() {

				@Override
				public void register(String extension, Function<Path, WatchedFileListener> ctor) {
					// Do nothing
				}

			});

	public abstract void register(String extension, Function<Path, WatchedFileListener> ctor);
}
