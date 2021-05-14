package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EquivalenceFile extends PluginFile {

  private static final Pattern TAB = Pattern.compile("\t");
  private Map<String, Set<String>> current = Map.of();

  @SuppressWarnings("unused")
  private final Definer<EquivalenceFile> definer;

  public EquivalenceFile(Path fileName, String instanceName, Definer<EquivalenceFile> definer) {
    super(fileName, instanceName);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    renderer.line("Map size", current.size());
  }

  @ShesmuMethod(
      name = "is_same",
      description = "Checks if the two provided names are considered equivalent.")
  public boolean isSame(
      @ShesmuParameter(description = "one name to check") String a,
      @ShesmuParameter(description = "the other name to check") String b) {
    if (a.equals(b)) {
      return true;
    }
    return current.getOrDefault(a, Set.of()).contains(b);
  }

  @Override
  public Optional<Integer> update() {
    final var map = new TreeMap<String, Set<String>>();
    try (final var lines = Files.lines(fileName(), StandardCharsets.UTF_8)) {
      lines
          .filter(l -> !l.startsWith("#") && !l.isBlank())
          .forEach(
              line -> {
                final var members =
                    TAB.splitAsStream(line).filter(m -> !m.isBlank()).collect(Collectors.toSet());
                if (members.size() > 1) {
                  for (final var member : members) {
                    map.put(member, members);
                  }
                }
              });
      current = map;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }
}
