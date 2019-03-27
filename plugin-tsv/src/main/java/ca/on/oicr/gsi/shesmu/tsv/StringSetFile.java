package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public final class StringSetFile extends PluginFile {

  private static final Predicate<String> GOOD_LINE = Pattern.compile("^\\s*[^#].*$").asPredicate();
  private static final Gauge badFile =
      Gauge.build(
              "shesmu_auto_update_bad_string_constants_file",
              "Whether a string constants file can't be read")
          .labelNames("filename")
          .register();
  private boolean good;

  private Set<String> values = Collections.emptySet();

  public StringSetFile(Path fileName, String instanceName) {
    super(fileName, instanceName);
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Count", values.size());
    renderer.line("Last read successful", good ? "Yes" : "No");
  }

  @ShesmuMethod(name = "$", type = "as", description = "Set of strings from {file}.")
  public Set<String> get() {
    return values;
  }

  @Override
  public Optional<Integer> update() {
    try (Stream<String> lines = Files.lines(fileName())) {
      values = lines.filter(GOOD_LINE).collect(Collectors.toCollection(TreeSet::new));
      badFile.labels(fileName().toString()).set(0);
      good = true;
    } catch (final Exception e) {
      e.printStackTrace();
      badFile.labels(fileName().toString()).set(1);
      good = false;
    }
    return Optional.empty();
  }
}
