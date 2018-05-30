package ca.on.oicr.gsi.shesmu;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * A source to find dumpers by name
 */
public interface DumperSource extends LoadedConfiguration {

	static final ServiceLoader<DumperSource> LOADER = ServiceLoader.load(DumperSource.class);

	/**
	 * Find a dumper for the provided name
	 * 
	 * @param name
	 *            the dumper to find
	 * @return a dumper to write to; if one is not defined, a dumper that discards
	 *         the output is provided
	 */
	@RuntimeInterop
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

	/**
	 * Find a dumper
	 * 
	 * @param name
	 *            the dumper to find
	 * @return the dumper if found, or an empty optional if none is available
	 */
	public Optional<Dumper> findDumper(String name);
}
