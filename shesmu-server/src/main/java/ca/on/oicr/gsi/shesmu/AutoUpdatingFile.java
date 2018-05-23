package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import io.prometheus.client.Gauge;

/**
 * Creates a watched file that will be notified when the file changes on disk
 */
public abstract class AutoUpdatingFile {
	private static final Gauge lastUpdated = Gauge
			.build("shesmu_auto_update_timestamp",
					"The last time, in seconds since the epoch, that a file was updated.")
			.labelNames("filename").register();

	private final Path fileName;

	private volatile boolean running = true;

	private long timeout = 0;

	private final Thread watching = new Thread(this::run, "file-watcher");

	public AutoUpdatingFile(Path fileName) {
		super();
		this.fileName = fileName;
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	/**
	 * The file being monitored
	 */
	public final Path fileName() {
		return fileName;
	}

	/**
	 * If set to a non-zero value, the file will be “updated” after the specified
	 * number of minutes even if the contents on disk hasn't been changed.
	 * 
	 * This is reset when {{@link #update()} is called, so if the update needs to be
	 * tried again, it must call this method every time.
	 * 
	 * @param timeout
	 *            the number of minutes to wait
	 */
	protected void retry(long timeout) {
		this.timeout = timeout;
	}

	private void run() {
		lastUpdated.labels(fileName.toString()).setToCurrentTime();
		update();
		try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
			fileName.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
			while (running) {
				try {
					final WatchKey wk = timeout > 0 ? watchService.poll(timeout, TimeUnit.MINUTES)
							: watchService.take();
					timeout = 0;
					if (wk == null) {
						lastUpdated.labels(fileName.toString()).setToCurrentTime();
						update();
					} else {
						for (final WatchEvent<?> event : wk.pollEvents()) {
							final Path changed = (Path) event.context();
							if (changed.getFileName().equals(fileName.getFileName())) {
								lastUpdated.labels(fileName.toString()).setToCurrentTime();
								update();
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

	/**
	 * Begin watching the file and call {{@link #update()} immediately.
	 */
	public void start() {
		running = true;
		watching.start();
	}

	/**
	 * Stop watching the file.
	 */
	public void stop() {
		running = false;
		watching.interrupt();
	}

	/**
	 * Called when the file is changed and once initially when started.
	 */
	protected abstract void update();
}
