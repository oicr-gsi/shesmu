package ca.on.oicr.gsi.shesmu.gsicommon;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IUSUtils {

  private static final Pattern COLON = Pattern.compile(":");
  private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");
  private static final Pattern TISSUE_REGEX =
      Pattern.compile(
          "^([A-Z0-9]{3,10}_\\d{4,}_\\d{2,}|[A-Z0-9]{3,10}_\\d{4,}_[A-Zn][a-z]_[A-Zn]_(nn|\\d{2})_\\d+-\\d+)$");
  public static final Tuple UNKNOWN_VERSION = new Tuple(0L, 0L, 0L);
  private static final Pattern WORKFLOW_VERSION2 = Pattern.compile("^(\\d+)\\.(\\d+)$");
  private static final Pattern WORKFLOW_VERSION3 = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
  private static final Pattern WORKFLOW_VERSION4 =
      Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)$");

  public static Map<String, Set<String>> attributes(
      SortedMap<String, SortedSet<String>> attributes) {
    if (attributes == null) {
      return Map.of();
    }
    return attributes.entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
  }

  public static long parseLaneNumber(String laneName) {
    try {
      return Long.parseUnsignedLong(laneName);
    } catch (final NumberFormatException e) {
      // Try something else.
    }
    final var laneMatcher = LANE_NUMBER.matcher(laneName);
    if (laneMatcher.matches()) {
      return parseLong(laneMatcher.group(1));
    }
    return 0;
  }

  public static long parseLong(String input) {
    try {
      return Long.parseLong(input);
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  public static Optional<Tuple> parseWorkflowVersion(String input) {
    if (input != null) {
      final var m3 = WORKFLOW_VERSION3.matcher(input);
      if (m3.matches()) {
        return Optional.of(
            new Tuple(
                Long.parseLong(m3.group(1)),
                Long.parseLong(m3.group(2)),
                Long.parseLong(m3.group(3))));
      }
      final var m2 = WORKFLOW_VERSION2.matcher(input);
      if (m2.matches()) {
        return Optional.of(new Tuple(Long.parseLong(m2.group(1)), Long.parseLong(m2.group(2)), 0L));
      }
      // Migrated Niassa workflow versions might have 4 components: three version components
      // followed by the Niassa workflow accession. For Shesmu purposes, drop the Niassa workflow
      // accession.
      final var m4 = WORKFLOW_VERSION4.matcher(input);
      if (m4.matches()) {
        return Optional.of(
            new Tuple(
                Long.parseLong(m4.group(1)),
                Long.parseLong(m4.group(2)),
                Long.parseLong(m4.group(3))));
      }
    }
    return Optional.empty();
  }

  public static <T> Optional<T> singleton(
      Collection<T> items, Consumer<String> isBad, boolean required) {
    if (items == null) {
      if (required) {
        isBad.accept("null");
      }
      return Optional.empty();
    }
    switch (items.size()) {
      case 0:
        if (required) {
          isBad.accept("empty");
        }
        return Optional.empty();
      case 1:
        return Optional.of(items.iterator().next());
      default:
        isBad.accept("multiple");
        return Optional.of(items.iterator().next());
    }
  }

  public static <T> Stream<T> stream(Collection<T> collection) {
    return collection == null ? Stream.empty() : collection.stream();
  }

  public static String tissue(String parents) {
    // This tissue are order as a climb up the sample hierarchy (i.e., from library to identity), so
    // the first tissue we see is the most derived, which is what we want.
    return COLON
        .splitAsStream(parents)
        .filter(parent -> TISSUE_REGEX.matcher(parent).matches())
        .findFirst()
        .orElse("");
  }

  private IUSUtils() {}
}
