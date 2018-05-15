package ca.on.oicr.gsi.shesmu;

import java.util.Optional;
import java.util.ServiceLoader;

public interface DumperSource extends LoadedConfiguration {

	static final ServiceLoader<DumperSource> LOADER = ServiceLoader.load(DumperSource.class);

	public static Dumper find(String name) {
		for (DumperSource source : LOADER) {
			Optional<Dumper> dumper = source.findDumper(name);
			if (dumper.isPresent()) {
				return dumper.get();
			}
		}
		return new Dumper() {

			@Override
			public void write(Object[] values) {
				// Do nothing.
			}

			@Override
			public void stop() {
				// Do nothing.
			}

			@Override
			public void start() {
				// Do nothing.
			}
		};
	}

	public Optional<Dumper> findDumper(String name);
}
