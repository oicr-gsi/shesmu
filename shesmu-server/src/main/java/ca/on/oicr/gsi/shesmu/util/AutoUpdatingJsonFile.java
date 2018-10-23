package ca.on.oicr.gsi.shesmu.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import io.prometheus.client.Gauge;

/**
 * Creates a watched JSON file that will be notified when the file changes on
 * disk and parsed
 */
public abstract class AutoUpdatingJsonFile<T> implements WatchedFileListener, FileBound {

	private static final Gauge goodJson = Gauge
			.build("shesmu_auto_update_good_json", "Whether a JSON configuration file is valid.").labelNames("filename")
			.register();

	private final Class<T> clazz;

	private final Path fileName;

	/**
	 * Creates a new monitor
	 *
	 * @param fileName
	 *            the file to monitor
	 * @param clazz
	 *            the class to parse the JSON file as
	 */
	public AutoUpdatingJsonFile(Path fileName, Class<T> clazz) {
		this.fileName = fileName;
		this.clazz = clazz;
	}

	public final Path fileName() {
		return fileName;
	}

	@Override
	public void start() {
		update();
	}

	@Override
	public void stop() {
		// Do nothing.
	}

	@Override
	public final Optional<Integer> update() {
		try {
			final T value = RuntimeSupport.MAPPER.readValue(Files.readAllBytes(fileName()), clazz);
			goodJson.labels(fileName().toString()).set(1);
			return update(value);
		} catch (final Exception e) {
			e.printStackTrace();
			goodJson.labels(fileName().toString()).set(0);
			return Optional.empty();
		}
	}

	/**
	 * Called when the underlying file has been parsed
	 *
	 * @param value
	 *            the parsed file contents
	 */
	protected abstract Optional<Integer> update(T value);

}
