package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public class RangeFile extends PluginFile {
  private static final Predicate<String> COMMENTS =
      Pattern.compile("^\\s(#.*)?$").asPredicate().negate();
  private static final Pattern TAB = Pattern.compile("\\t");

  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private final Definer<RangeFile> definer;

  private NavigableMap<Instant, String> ranges = Collections.emptyNavigableMap();

  public RangeFile(Path fileName, String instanceName, Definer<RangeFile> definer) {
    super(fileName, instanceName);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {}

  @ShesmuMethod(
      name = "$",
      type = "t2ds",
      description =
          "Gets the value for a range of time windows specified in {file}. Time before the first window will return the empty string.")
  public Tuple get(
      @ShesmuParameter(description = "The time to look for in the window.") Instant time) {
    final Map.Entry<Instant, String> entry = ranges.floorEntry(time);
    return entry == null
        ? new Tuple(Instant.EPOCH, "")
        : new Tuple(entry.getKey(), entry.getValue());
  }

  @Override
  public Optional<Integer> update() {
    try (final Stream<String> lines = Files.lines(fileName(), StandardCharsets.UTF_8)) {
      ranges =
          lines
              .filter(COMMENTS)
              .map(TAB::split)
              .collect(
                  Collectors.toMap(
                      parts -> Instant.parse(parts[0]),
                      parts -> parts[1],
                      (a, b) -> {
                        throw new IllegalStateException("Duplicate time stamps.");
                      },
                      TreeMap::new));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }
}
