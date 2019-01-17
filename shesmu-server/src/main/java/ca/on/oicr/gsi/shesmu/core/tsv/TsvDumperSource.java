package ca.on.oicr.gsi.shesmu.core.tsv;

import ca.on.oicr.gsi.shesmu.Dumper;
import ca.on.oicr.gsi.shesmu.DumperSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class TsvDumperSource implements DumperSource {

  private class DumperConfiguration extends AutoUpdatingJsonFile<ObjectNode> {
    private Map<String, Path> paths = Collections.emptyMap();

    public DumperConfiguration(Path fileName) {
      super(fileName, ObjectNode.class);
    }

    public ConfigurationSection configuration() {
      return new ConfigurationSection(String.format("TSV Dumpers from %s", fileName())) {

        @Override
        public void emit(SectionRenderer renderer) throws XMLStreamException {
          paths
              .entrySet()
              .stream()
              .sorted(Comparator.comparing(Entry::getKey))
              .forEach(pair -> renderer.line(pair.getKey(), pair.getValue().toString()));
        }
      };
    }

    public Dumper get(String name) {
      final Path path = paths.get(name);
      return path == null
          ? null
          : new Dumper() {
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
              output.ifPresent(
                  o -> {
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
      paths =
          RuntimeSupport.stream(value.fields())
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
  public Stream<ConfigurationSection> listConfiguration() {
    return configurations.stream().map(DumperConfiguration::configuration);
  }
}
