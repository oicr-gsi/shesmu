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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class TsvDumperFileType extends PluginFileType<TsvDumperFileType.DumperConfiguration> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static class DumperConfiguration extends JsonPluginFile<ObjectNode> {
    private Map<String, Path> paths = Map.of();

    public DumperConfiguration(
        Path fileName,
        String instanceName,
        Definer<TsvDumperFileType.DumperConfiguration> definer) {
      super(fileName, instanceName, MAPPER, ObjectNode.class);
    }

    public void configuration(SectionRenderer renderer) {
      paths.entrySet().stream()
          .sorted(Entry.comparingByKey())
          .forEach(pair -> renderer.line(pair.getKey(), pair.getValue().toString()));
    }

    @Override
    public Stream<Dumper> findDumper(String name, String[] columns, Imyhat... types) {
      final var path = paths.get(name);
      return path == null
          ? Stream.empty()
          : Stream.of(
              new Dumper() {
                private final Optional<PrintStream> output;

                {
                  Optional<PrintStream> output;
                  try {
                    final var stream = new PrintStream(path.toFile());
                    for (var it = 0; it < columns.length; it++) {
                      if (it > 0) {
                        stream.print("\t");
                      }
                      stream.print(columns[it]);
                    }
                    stream.println();
                    output = Optional.of(stream);
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
                        for (var it = 0; it < values.length; it++) {
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
    super(MethodHandles.lookup(), DumperConfiguration.class, ".tsvdump", "table");
  }

  @Override
  public DumperConfiguration create(
      Path filePath, String instanceName, Definer<DumperConfiguration> definer) {
    return new DumperConfiguration(filePath, instanceName, definer);
  }
}
