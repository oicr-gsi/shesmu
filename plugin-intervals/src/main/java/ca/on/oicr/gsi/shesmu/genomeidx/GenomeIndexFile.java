package ca.on.oicr.gsi.shesmu.genomeidx;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenomeIndexFile extends JsonPluginFile<Configuration> {
  private static final Pattern FILE_NAME = Pattern.compile("(?<genome>[^.]+)\\.fai");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern TAB = Pattern.compile("\\t");
  private Map<Pair<String, String>, Long> sortPositions = Map.of();

  public GenomeIndexFile(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    renderer.line("Total Chromosomes", sortPositions.size());
  }

  private List<Pair<Pair<String, String>, Long>> readChromosomes(String genome, Path file) {
    try (final var faiContents = Files.lines(file, StandardCharsets.US_ASCII)) {
      return faiContents
          .map(
              new Function<String, Pair<Pair<String, String>, Long>>() {
                private long index;

                @Override
                public Pair<Pair<String, String>, Long> apply(String l) {
                  return new Pair<>(new Pair<>(genome, TAB.split(l)[0]), index++);
                }
              })
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @ShesmuMethod(description = "Get the sorting order of a chromosome within a genome")
  public long sort_order(
      @ShesmuParameter(description = "The target genome name (e.g., hg19, mm10)") String genome,
      @ShesmuParameter(description = "The chromosome name (e.g., chr12, chrM)") String chromosome) {
    return sortPositions.getOrDefault(new Pair<>(genome, chromosome), Long.MAX_VALUE);
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    final var root = fileName().resolveSibling(value.getDirectory());
    try (final var files = Files.walk(root, 1)) {
      this.sortPositions =
          files
              .flatMap(
                  file -> {
                    final var match = FILE_NAME.matcher(file.getFileName().toString());
                    if (match.matches()) {
                      return readChromosomes(match.group("genome"), file).stream();
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
