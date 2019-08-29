package ca.on.oicr.gsi.shesmu.rsconfig;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pde.deciders.Rsconfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;

public class IntervalsFile extends PluginFile {
  private static final Pattern COMMA = Pattern.compile(",");
  private Optional<Rsconfig> config = Optional.empty();
  private boolean configGood = false;

  public IntervalsFile(Path filePath, String instanceName, Definer<IntervalsFile> definer) {
    super(filePath, instanceName);
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Config Good", configGood ? "Yes" : "No");
  }

  @ShesmuMethod(name = "$", description = "Get RsConfig data from {file}.")
  public Optional<String> get(
      @ShesmuParameter(description = "The template type") String templateType,
      @ShesmuParameter(description = "The resequencing type (kit).") String resequencingType,
      @ShesmuParameter(description = "The configuration key/file type") String configKey) {
    return config.map(r -> r.get(templateType, resequencingType, configKey));
  }

  @ShesmuMethod(
      name = "$_chromosomes",
      description = "Get RsConfig chromosomes as a collection from {file}.")
  public Optional<Set<String>> getChromosomes(
      @ShesmuParameter(description = "The template type") String templateType,
      @ShesmuParameter(description = "The the resequencing type (kit).") String resequencingType) {
    return config
        .map(r -> r.get(templateType, resequencingType, "chromosomes"))
        .map(s -> new TreeSet<>(Arrays.asList(COMMA.split(s))));
  }

  @Override
  public Optional<Integer> update() {
    try {
      config = Optional.of(new Rsconfig(fileName().toFile()));
      configGood = true;
    } catch (Exception e) {
      e.printStackTrace();
      configGood = false;
    }
    return Optional.empty();
  }
}
