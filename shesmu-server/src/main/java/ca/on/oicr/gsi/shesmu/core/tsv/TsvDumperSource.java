package ca.on.oicr.gsi.shesmu.core.tsv;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Dumper;
import ca.on.oicr.gsi.shesmu.DumperSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;

@MetaInfServices
public class TsvDumperSource implements DumperSource {

	private class DumperConfiguration extends AutoUpdatingJsonFile<ObjectNode> {
		private Map<String, Path> paths = Collections.emptyMap();

		public DumperConfiguration(Path fileName) {
			super(fileName, ObjectNode.class);
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>(String.format("TSV Dumpers from %s", fileName()),
					paths.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString())));
		}

		public Dumper get(String name) {
			final Path path = paths.get(name);
			return path == null ? null : new Dumper() {
				private Optional<PrintStream> output = Optional.empty();

				@Override
				public void start() {
					try {
						output = Optional.of(new PrintStream(path.toFile()));
					} catch (final FileNotFoundException e) {
						e.printStackTrace();
						output = Optional.empty();
					}
				}

				@Override
				public void stop() {
					output.ifPresent(PrintStream::close);
				}

				@Override
				public void write(Object... values) {
					output.ifPresent(o -> {
						for (int it = 0; it < values.length; it++) {
							if (it > 0) {
								o.print("\t");
							}
							o.print(values[it]);
						}
						o.println();
					});
				}
			};
		}

		@Override
		protected Optional<Integer> update(ObjectNode value) {
			paths = RuntimeSupport.stream(value.fields())
					.collect(Collectors.toMap(Entry::getKey, e -> Paths.get(e.getValue().asText())));
			return Optional.empty();
		}
	}

	private static final String EXTENSION = ".tsvdump";
	private final AutoUpdatingDirectory<DumperConfiguration> configurations;

	public TsvDumperSource() {
		configurations = new AutoUpdatingDirectory<>(EXTENSION, DumperConfiguration::new);
	}

	@Override
	public Optional<Dumper> findDumper(String name, Imyhat... types) {
		return configurations.stream().map(c -> c.get(name)).filter(Objects::nonNull).findFirst();
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(DumperConfiguration::configuration);
	}

}
