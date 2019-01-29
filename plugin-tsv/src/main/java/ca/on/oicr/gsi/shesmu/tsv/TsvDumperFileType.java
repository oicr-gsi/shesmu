package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class TsvDumperFileType extends PluginFileType<TsvDumperFileType.DumperConfiguration> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  class DumperConfiguration extends JsonPluginFile<ObjectNode> {
    private Map<String, Path> paths = Collections.emptyMap();

    public DumperConfiguration(Path fileName, String instanceName, Definer definer) {
      super(fileName, instanceName, MAPPER, ObjectNode.class);
    }

    public void configuration(SectionRenderer renderer) throws XMLStreamException {
      paths
          .entrySet()
          .stream()
          .sorted(Comparator.comparing(Entry::getKey))
          .forEach(pair -> renderer.line(pair.getKey(), pair.getValue().toString()));
    }

    @Override
    public Stream<Dumper> findDumper(String name, Imyhat... types) {
      final Path path = paths.get(name);
      return path == null
          ? Stream.empty()
          : Stream.of(
              new Dumper() {
                private final Optional<PrintStream> output;

                {
                  Optional<PrintStream> output;
                  try {
                    output = Optional.of(new PrintStream(path.toFile()));
                  } catch (final FileNotFoundException e) {
                    e.printStackTrace();
                    output = Optional.empty();
                  }
                  this.output = output;
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
              });
    }

    @Override
    protected Optional<Integer> update(ObjectNode value) {
      paths =
          Utils.stream(value.fields())
              .collect(Collectors.toMap(Entry::getKey, e -> Paths.get(e.getValue().asText())));
      return Optional.empty();
    }
  }

  public TsvDumperFileType() {
    super(MethodHandles.lookup(), DumperConfiguration.class, ".tsvdump");
  }

  @Override
  public DumperConfiguration create(Path filePath, String instanceName, Definer definer) {
    return new DumperConfiguration(filePath, instanceName, definer);
  }
}
