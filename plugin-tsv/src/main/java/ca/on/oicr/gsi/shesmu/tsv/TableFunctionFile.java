package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.stream.XMLStreamException;

class TableFunctionFile extends PluginFile {

  static final class Table implements VariadicFunction {
    private final List<Object[]> attempts;
    private final Object defaultValue;

    public Table(List<Object[]> attempts, Object defaultValue) {
      super();
      this.attempts = attempts;
      this.defaultValue = defaultValue;
    }

    @Override
    public Object apply(Object... parameters) {
      return attempts
          .stream()
          .map(
              attempt ->
                  IntStream.range(0, parameters.length)
                          .allMatch(i -> attempt[i] == null || attempt[i].equals(parameters[i]))
                      ? attempt[attempt.length - 1]
                      : null)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(defaultValue);
    }
  }

  private static final Gauge tableBad =
      Gauge.build("shesmu_tsv_lookup_bad", "A TSV/CSV lookup table is badly formed.")
          .labelNames("fileName")
          .register();

  private final Definer definer;

  private final Pattern separator;

  private boolean good;

  public TableFunctionFile(
      Path fileName, String instanceName, Definer<TableFunctionFile> definer, Pattern separator) {
    super(fileName, instanceName);
    this.definer = definer;
    this.separator = separator;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Is valid?", good ? "Yes" : "No");
  }

  @Override
  public Optional<Integer> update() {
    good = false;
    try {
      final List<String> lines = Files.readAllLines(fileName());

      if (lines.size() < 2) {
        tableBad.labels(fileName().toString()).set(1);
        return Optional.empty();
      }

      final List<BaseImyhat> types =
          separator.splitAsStream(lines.get(0)).map(Imyhat::forName).collect(Collectors.toList());
      if (types.size() < 2) {
        tableBad.labels(fileName().toString()).set(1);
        System.err.printf("%s header has too few columns: %d\n", fileName(), types.size());
        return Optional.empty();
      }

      final List<String[]> grid =
          lines.stream().skip(1).map(separator::split).collect(Collectors.toList());

      if (grid.stream().anyMatch(columns -> columns.length != types.size())) {
        tableBad.labels(fileName().toString()).set(1);
        System.err.printf("%s has row with columns not matching header\n", fileName());
        return Optional.empty();
      }

      final List<Object[]> attempts =
          grid.stream()
              .map(
                  columns -> {
                    final Object[] attempt = new Object[types.size()];
                    for (int index = 0; index < columns.length; index++) {
                      if (index == columns.length - 1 || !columns[index].equals("*")) {
                        attempt[index] = types.get(index).parse(columns[index]);
                      }
                    }
                    return attempt;
                  })
              .collect(Collectors.toList());

      definer.defineFunction(
          name(),
          String.format("Table-defined lookup from %s.", fileName()),
          types.get(types.size() - 1),
          new Table(attempts, types.get(types.size() - 1).defaultValue()),
          types
              .stream()
              .limit(types.size() - 1)
              .map(
                  new Function<Imyhat, FunctionParameter>() {
                    int index;

                    @Override
                    public FunctionParameter apply(Imyhat type) {
                      return new FunctionParameter(String.format("arg%d", ++index), type);
                    }
                  })
              .toArray(FunctionParameter[]::new));
      tableBad.labels(fileName().toString()).set(0);
      good = true;
    } catch (final IOException e) {
      e.printStackTrace();
      return Optional.empty();
    }

    return Optional.empty();
  }
}
