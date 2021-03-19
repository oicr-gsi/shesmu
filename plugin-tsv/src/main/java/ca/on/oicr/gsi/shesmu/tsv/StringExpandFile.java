package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class StringExpandFile extends PluginFile {

  private static final Pattern TAB = Pattern.compile("\t");
  private static final Gauge tableBad =
      Gauge.build("shesmu_strexpand_lookup_bad", "A string expansion table is badly formed.")
          .labelNames("fileName")
          .register();
  private final Definer<StringExpandFile> definer;
  private Map<String, Set<String>> expansions = Map.of();
  private boolean good;

  public StringExpandFile(Path fileName, String instanceName, Definer<StringExpandFile> definer) {
    super(fileName, instanceName);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    renderer.line("Is valid?", good ? "Yes" : "No");
  }

  @ShesmuMethod(
      name = "get",
      description =
          "Expand a string to a set of strings or a set containing only the input string.")
  public Set<String> get(
      @ShesmuParameter(
              description =
                  "The input string to find the in the table; if it does not exist in the table, it is returned as a list containing the input")
          String input) {
    return expansions.getOrDefault(input, Set.of(input));
  }

  @Override
  public Optional<Integer> update() {
    good = false;
    try (var lines = Files.lines(fileName(), StandardCharsets.UTF_8)) {
      expansions =
          lines
              .map(String::trim)
              .filter(l -> !l.startsWith("#"))
              .map(TAB::split)
              .collect(
                  Collectors.toMap(x -> x[0], x -> new TreeSet<>(List.of(x).subList(1, x.length))));
      good = true;
    } catch (final Exception e) {
      good = false;
      e.printStackTrace();
    }
    tableBad.labels(fileName().toString()).set(good ? 0 : 1);
    return Optional.empty();
  }
}
