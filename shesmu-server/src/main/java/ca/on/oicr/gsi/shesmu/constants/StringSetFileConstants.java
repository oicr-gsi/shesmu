package ca.on.oicr.gsi.shesmu.constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.WatchedFileListener;
import io.prometheus.client.Gauge;

/**
 * Read constants from a file with one string per line (and automatically
 * reparse those files if they change)
 */
@MetaInfServices(ConstantSource.class)
public class StringSetFileConstants implements ConstantSource {

	private class ConstantsFile extends Constant implements WatchedFileListener {
		private Set<String> constants = Collections.emptySet();

		private final Path fileName;
		private final long id;

		public ConstantsFile(Path fileName) {
			super(RuntimeSupport.removeExtension(fileName, EXTENSION), Imyhat.STRING.asList(),
					String.format("set of strings from file %s", fileName));
			this.fileName = fileName;
			id = idGenerator.incrementAndGet();
			cache.put(id, this);
		}

		@Override
		protected void load(GeneratorAdapter methodGen) {
			methodGen.push(id);
			methodGen.invokeStatic(Type.getType(StringSetFileConstants.class),
					new Method("fetch", Type.getType(Set.class), new Type[] { Type.LONG_TYPE }));
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
				constants = new TreeSet<>(Files.readAllLines(fileName));
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

	private static final AtomicLong idGenerator = new AtomicLong();

	@RuntimeInterop
	public static final Set<String> fetch(long constantsId) {
		return cache.get(constantsId).constants;
	}

	private final AutoUpdatingDirectory<ConstantsFile> files;

	public StringSetFileConstants() {
		files = new AutoUpdatingDirectory<>(EXTENSION, ConstantsFile::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.of(new Pair<>("Constants",
				files.stream().collect(Collectors.toMap(x -> x.name(), x -> x.fileName.toString()))));
	}

	@Override
	public Stream<? extends Constant> queryConstants() {
		return files.stream();
	}

}
