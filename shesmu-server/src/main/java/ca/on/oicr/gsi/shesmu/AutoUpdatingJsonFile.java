package ca.on.oicr.gsi.shesmu;

import java.nio.file.Files;
import java.nio.file.Path;

import io.prometheus.client.Gauge;

public abstract class AutoUpdatingJsonFile<T> extends AutoUpdatingFile {

	private static final Gauge goodJson = Gauge
			.build("shesmu_auto_update_good_json", "Whether a JSON configuration file is valid.").labelNames("filename")
			.register();

	private Class<T> clazz;

	public AutoUpdatingJsonFile(Path fileName, Class<T> clazz) {
		super(fileName);
		this.clazz = clazz;
	}

	@Override
	protected final void update() {
		try {
			final T value = RuntimeSupport.MAPPER.readValue(Files.readAllBytes(fileName()), clazz);
			goodJson.labels(fileName().toString()).set(1);
			update(value);
		} catch (final Exception e) {
			e.printStackTrace();
			goodJson.labels(fileName().toString()).set(0);
		}
	}

	protected abstract void update(T value);

}
