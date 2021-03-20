package ca.on.oicr.gsi.shesmu.tsv;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads <code>maintenance.schedule</code> which is a schedule or maintenance windows during which
 * all activity should be throttled.
 */
public class MaintenanceSchedule extends PluginFileType<MaintenanceSchedule.ScheduleReader> {

  static class ScheduleReader extends PluginFile {

    private List<Instant[]> windows = List.of();

    public ScheduleReader(Path fileName, String instanceName) {
      super(fileName, instanceName);
    }

    @ShesmuMethod(description = "Check if a particular date was in maintenance.")
    public boolean check(Instant date) {
      return windows.stream()
          .anyMatch(window -> date.isAfter(window[0]) && date.isBefore(window[1]));
    }

    @Override
    public void configuration(SectionRenderer renderer) {
      renderer.line("Current State", inMaintenanceWindow() ? "Throttled" : "Permit");
      for (var i = 0; i < windows.size(); i++) {
        renderer.line(
            String.format("Window %d", i),
            String.format("%s - %s", windows.get(i)[0], windows.get(i)[1]));
      }
    }

    public boolean inMaintenanceWindow() {
      final var now = Instant.now();
      return check(now);
    }

    @Override
    public Stream<String> isOverloaded(Set<String> services) {
      return (name().equals("maintenance") || services.contains(name())) && inMaintenanceWindow()
          ? Stream.of(name())
          : Stream.empty();
    }

    @Override
    public Optional<Integer> update() {
      try {
        windows =
            Files.readAllLines(fileName()).stream()
                .map(
                    line ->
                        BLANK
                            .splitAsStream(line)
                            .limit(2)
                            .map(
                                str ->
                                    ZonedDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME)
                                        .toInstant())
                            .toArray(Instant[]::new))
                .filter(times -> times[0].isBefore(times[1]))
                .sorted(Comparator.comparing(a -> a[0]))
                .collect(Collectors.toList());
      } catch (final IOException e) {
        e.printStackTrace();
      }
      return Optional.empty();
    }
  }

  private static final Pattern BLANK = Pattern.compile("\\s+");

  public MaintenanceSchedule() {
    super(MethodHandles.lookup(), ScheduleReader.class, ".schedule", "schedule");
  }

  @Override
  public ScheduleReader create(
      Path filePath, String instanceName, Definer<MaintenanceSchedule.ScheduleReader> definer) {
    return new ScheduleReader(filePath, instanceName);
  }
}
