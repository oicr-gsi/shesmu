package ca.on.oicr.gsi.shesmu.throttler.scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AutoUpdatingFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Throttler;

/**
 * Reads <tt>maintenance.tsv</tt> which is a schedule or maintenance windows
 * during which all activity should be throttled.
 */
@MetaInfServices(Throttler.class)
public class MaintenanceSchedule implements Throttler {

	private static class ScheduleReader extends AutoUpdatingFile {

		private List<Instant[]> windows = Collections.emptyList();

		public ScheduleReader(Path fileName) {
			super(fileName);
		}

		public boolean inMaintenanceWindow() {
			final Instant now = Instant.now();
			return windows.stream().anyMatch(window -> now.isAfter(window[0]) && now.isBefore(window[2]));
		}

		@Override
		protected void update() {
			try {
				windows = Files.readAllLines(fileName()).stream()//
						.map(line -> BLANK.splitAsStream(line)//
								.limit(2)//
								.map(str -> ZonedDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME).toInstant())//
								.toArray(Instant[]::new))//
						.filter(times -> times[0].isBefore(times[1]))//
						.sorted((a, b) -> a[0].compareTo(b[0]))//
						.collect(Collectors.toList());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static final Pattern BLANK = Pattern.compile("\\s+");

	private final Optional<ScheduleReader> schedule = RuntimeSupport.dataFile("maintenance.tsv")//
			.map(ScheduleReader::new);

	@Override
	public boolean isOverloaded(Set<String> services) {
		return schedule.map(ScheduleReader::inMaintenanceWindow).orElse(false);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return schedule.map(s -> {
			final Map<String, String> properties = new HashMap<>();
			properties.put("file", s.fileName().toString());
			properties.put("state", s.inMaintenanceWindow() ? "throttled" : "permit");
			for (int i = 0; i < s.windows.size(); i++) {
				properties.put(String.format("Window %d", i),
						String.format("%s - %s", s.windows.get(i)[0], s.windows.get(i)[1]));
			}
			return Stream.of(new Pair<>("Maintenance Window Throttler", properties));
		}).orElseGet(Stream::empty);
	}

}
