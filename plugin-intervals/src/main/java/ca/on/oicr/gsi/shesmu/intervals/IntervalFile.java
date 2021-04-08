package ca.on.oicr.gsi.shesmu.intervals;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntervalFile extends JsonPluginFile<Configuration> {
  private static final Pattern COMMA = Pattern.compile(",");
  private static final Pattern FILE_NAME =
      Pattern.compile("(?<panel>[^.]+)\\.(?<library>[A-Z]+(?:,[A-Z]+)*)\\.(?<genome>[^.]+)\\.bed");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern TAB = Pattern.compile("\\t");
  private static final Gauge badBed =
      Gauge.build("shesmu_intervals_bad_bed", "Some records are corrupted in a BED file")
          .labelNames("filename", "bedfile")
          .register();
  private Map<IntervalKey, Tuple> files = Map.of();

  public IntervalFile(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    renderer.line("Records", files.size());
  }

  @ShesmuMethod(
      description =
          "Get the best available panel configuration. A fallback panel may be used if one is available.",
      type = "qo2chromosomes$msiinterval_file$p")
  public Optional<Tuple> get(
      @ShesmuParameter(description = "The library type (e.g., WG, TS)") String library,
      @ShesmuParameter(description = "The target genome name (e.g., hg19, mm10)") String genome,
      @ShesmuParameter(description = "The targeted sequencing kit (e.g., IDT xGen Pan-Cancer)")
          String panel) {
    return Optional.ofNullable(files.get(new IntervalKey(panel, library, genome)))
        .or(() -> Optional.ofNullable(files.get(new IntervalKey("ALL", library, genome))));
  }

  private Map<String, Long> readChromosomes(Path file) {
    badBed.labels(fileName().toString(), file.toString()).set(0);
    try (final var bedContents = Files.lines(file, StandardCharsets.US_ASCII)) {
      return bedContents
          .map(
              l -> {
                final var fields = TAB.split(l);
                var length = 0L;
                if (fields.length >= 3) {
                  try {
                    length = Math.abs(Long.parseLong(fields[2]) - Long.parseLong(fields[1]));
                  } catch (NumberFormatException e) {
                    badBed.labels(fileName().toString(), file.toString()).set(1);
                  }
                }
                return new Pair<>(fields[0], length);
              })
          .collect(
              Collectors.groupingBy(
                  Pair::first, TreeMap::new, Collectors.summingLong(Pair::second)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<String> readPanelNames(Path aliasFile, String panelName) {
    try (final var panelsContents =
        Stream.concat(
            Stream.of(panelName),
            Files.exists(aliasFile)
                ? Files.lines(aliasFile, StandardCharsets.US_ASCII).filter(s -> !s.isBlank())
                : Stream.empty())) {
      return panelsContents.collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    final var root = fileName().resolveSibling(value.getDirectory());
    try (final var files = Files.walk(root, 1)) {
      this.files =
          files
              .flatMap(
                  file -> {
                    final var match = FILE_NAME.matcher(file.getFileName().toString());
                    if (match.matches()) {
                      final var bedFile =
                          Path.of(value.getReplacementPrefix()).resolve(root.relativize(file));
                      final var chromosomes = readChromosomes(file);
                      final var panels =
                          readPanelNames(
                              root.resolve(
                                  String.format(
                                      "%s.%s.%s.alias",
                                      match.group("panel"),
                                      match.group("library"),
                                      match.group("genome"))),
                              match.group("panel"));

                      return panels.stream()
                          .flatMap(
                              panel ->
                                  COMMA
                                      .splitAsStream(match.group("library"))
                                      .map(
                                          library ->
                                              new Pair<>(
                                                  new IntervalKey(
                                                      panel, library, match.group("genome")),
                                                  new Tuple(chromosomes, bedFile))));
                    } else {
                      return Stream.empty();
                    }
                  })
              .collect(Collectors.toMap(Pair::first, Pair::second));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Optional.of(10);
  }
}
