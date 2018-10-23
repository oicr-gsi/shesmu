package ca.on.oicr.gsi.shesmu.core.constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.FileBound;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;

/**
 * Read constants from a file with one string per line (and automatically
 * reparse those files if they change)
 */
@MetaInfServices(ConstantSource.class)
public class StringSetFileConstants implements ConstantSource {

	public final class ConstantsFile implements WatchedFileListener, FileBound {
		private final List<Constant> constants;

		private final Path fileName;
		private Set<String> values = Collections.emptySet();

		public ConstantsFile(Path fileName) {
			this.fileName = fileName;
			constants = RUNTIME_BINDING.bindConstants(this);
		}

		@Override
		public Path fileName() {
			return fileName;
		}

		@RuntimeInterop
		public Set<String> get() {
			return values;
		}

		@Override
		public void start() {
			update();
		}

		@Override
		public void stop() {
			// Do nothing
		}

		@Override
		public Optional<Integer> update() {
			try {
				values = new TreeSet<>(Files.readAllLines(fileName));
				badFile.labels(fileName.toString()).set(0);
			} catch (final Exception e) {
				e.printStackTrace();
				badFile.labels(fileName.toString()).set(1);
			}
			return Optional.empty();
		}
	}

	private static final Gauge badFile = Gauge
			.build("shesmu_auto_update_bad_string_constants_file", "Whether a string constants file can't be read")
			.labelNames("filename").register();
	private static final Map<Long, ConstantsFile> cache = new ConcurrentHashMap<>();
	private static final String EXTENSION = ".set";
	private static final RuntimeBinding<ConstantsFile> RUNTIME_BINDING = new RuntimeBinding<>(ConstantsFile.class,
			EXTENSION)//
					.constant("%s", "get", Imyhat.STRING.asList(), "Set of strings from file %2$s");

	@RuntimeInterop
	public static final Set<String> fetch(long constantsId) {
		return cache.get(constantsId).values;
	}

	private final AutoUpdatingDirectory<ConstantsFile> files;

	public StringSetFileConstants() {
		files = new AutoUpdatingDirectory<>(EXTENSION, ConstantsFile::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return Stream.of(new ConfigurationSection("Constants") {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				files.stream()//
						.sorted(Comparator.comparing(f -> f.fileName))//
						.forEach(f -> renderer.line(f.fileName.toString(), f.values.size()));
			}
		});
	}

	@Override
	public Stream<? extends Constant> queryConstants() {
		return files.stream().flatMap(f -> f.constants.stream());
	}

}
