package ca.on.oicr.gsi.shesmu.core.constants;

import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.stream.XMLStreamException;

public final class StringSetFile implements FileBackedConfiguration {

  private static final Gauge badFile =
      Gauge.build(
              "shesmu_auto_update_bad_string_constants_file",
              "Whether a string constants file can't be read")
          .labelNames("filename")
          .register();
  private final Path fileName;
  private boolean good;

  private Set<String> values = Collections.emptySet();

  public StringSetFile(Path fileName) {
    this.fileName = fileName;
  }

  @Override
  public ConfigurationSection configuration() {
    return new ConfigurationSection("String Set File: " + fileName()) {

      @Override
      public void emit(SectionRenderer renderer) throws XMLStreamException {
        renderer.line("Count", values.size());
        renderer.line("Last read successful", good ? "Yes" : "No");
      }
    };
  }

  @Override
  public Path fileName() {
    return fileName;
  }

  @ShesmuMethod(name = "$", type = "as", description = "Set of strings from {file}.")
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
      good = true;
    } catch (final Exception e) {
      e.printStackTrace();
      badFile.labels(fileName.toString()).set(1);
      good = false;
    }
    return Optional.empty();
  }
}
