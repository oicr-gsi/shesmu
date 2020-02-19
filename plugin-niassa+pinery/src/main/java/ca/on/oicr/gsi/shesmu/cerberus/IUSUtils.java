package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IUSUtils {
  public static Set<Tuple> attributes(SortedMap<String, SortedSet<String>> attributes) {
    if (attributes == null) {
      return Collections.emptySet();
    }
    return attributes
        .entrySet()
        .stream()
        .map(e -> new Tuple(e.getKey(), e.getValue()))
        .collect(Collectors.toCollection(ATTR_TYPE::newSet));
  }

  public static long parseLaneNumber(String laneName) {
    try {
      return Long.parseUnsignedLong(laneName);
    } catch (final NumberFormatException e) {
      // Try something else.
    }
    final Matcher laneMatcher = LANE_NUMBER.matcher(laneName);
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

  private static final Imyhat ATTR_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING.asList());
  private static final Pattern COLON = Pattern.compile(":");
  private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");
  private static final Pattern TISSUE_REGEX =
      Pattern.compile(
          "^([A-Z0-9]{3,10}_\\d{4,}_\\d{2,}|[A-Z0-9]{3,10}_\\d{4,}_[A-Zn][a-z]_[A-Zn]_(nn|\\d{2})_\\d+-\\d+)$");

  private IUSUtils() {}
}
