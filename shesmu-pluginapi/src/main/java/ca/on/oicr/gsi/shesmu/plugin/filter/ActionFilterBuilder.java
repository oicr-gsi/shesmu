package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ActionFilterBuilder<F> {
  /** Do a "transformation" of the JSON representation to an exact copy. */
  ActionFilterBuilder<ActionFilter> JSON =
      new ActionFilterBuilder<ActionFilter>() {
        @Override
        public ActionFilter added(Optional<Instant> start, Optional<Instant> end) {
          final ActionFilterAdded result = new ActionFilterAdded();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter addedAgo(long offset) {
          final ActionFilterAddedAgo result = new ActionFilterAddedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter and(Stream<ActionFilter> filters) {
          final ActionFilterAnd result = new ActionFilterAnd();
          result.setFilters(filters.toArray(ActionFilter[]::new));
          return result;
        }

        @Override
        public ActionFilter checked(Optional<Instant> start, Optional<Instant> end) {
          final ActionFilterChecked result = new ActionFilterChecked();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter checkedAgo(long offset) {
          final ActionFilterCheckedAgo result = new ActionFilterCheckedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter external(Optional<Instant> start, Optional<Instant> end) {
          final ActionFilterExternal result = new ActionFilterExternal();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return null;
        }

        @Override
        public ActionFilter externalAgo(long offset) {
          final ActionFilterExternalAgo result = new ActionFilterExternalAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter fromFile(String... files) {
          final ActionFilterSourceFile result = new ActionFilterSourceFile();
          result.setFiles(files);
          return result;
        }

        @Override
        public ActionFilter fromSourceLocation(Stream<SourceOliveLocation> locations) {
          final ActionFilterSourceLocation result = new ActionFilterSourceLocation();
          result.setLocations(
              locations.map(SourceOliveLocation::new).toArray(SourceOliveLocation[]::new));
          return result;
        }

        @Override
        public ActionFilter ids(List<String> ids) {
          final ActionFilterIds result = new ActionFilterIds();
          result.setIds(ids);
          return result;
        }

        @Override
        public ActionFilter isState(ActionState... states) {
          final ActionFilterStatus result = new ActionFilterStatus();
          result.setState(states);
          return result;
        }

        @Override
        public ActionFilter negate(ActionFilter filter) {
          // In the real use of the filter, the negation produces a new filter which returns the
          // logical not of the provided filter. In the case of JSON objects, the negation is a
          // field in the object. So, we copy the object and then negate it. Since it may already
          // have been negated, we flip the negation bit rather than setting it.
          final ActionFilter copy = filter.convert(this);
          copy.setNegate(!copy.isNegate());
          return copy;
        }

        @Override
        public ActionFilter or(Stream<ActionFilter> filters) {
          final ActionFilterOr result = new ActionFilterOr();
          result.setFilters(filters.toArray(ActionFilter[]::new));
          return result;
        }

        @Override
        public ActionFilter statusChanged(Optional<Instant> start, Optional<Instant> end) {
          final ActionFilterStatusChanged result = new ActionFilterStatusChanged();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter statusChangedAgo(long offset) {
          final ActionFilterStatusChangedAgo result = new ActionFilterStatusChangedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter tags(Stream<String> tags) {
          final ActionFilterTag result = new ActionFilterTag();
          result.setTags(tags.toArray(String[]::new));
          return result;
        }

        @Override
        public ActionFilter textSearch(Pattern pattern) {
          final ActionFilterRegex result = new ActionFilterRegex();
          result.setPattern(pattern.pattern());
          return result;
        }

        @Override
        public ActionFilter type(String... types) {
          final ActionFilterType result = new ActionFilterType();
          result.setTypes(types);
          return result;
        }
      };
  /**
   * Converts an action filter to a query string
   *
   * <p>The results include a precedence of the result. This can be discarded if used directly. If
   * more concatenation is required, it can be used to determine if parentheses are necessary.
   * Terminal expressions are 0, unary prefixes are 1, logical conjunction is 2, logical disjuncion
   * is 3.
   */
  ActionFilterBuilder<Pair<String, Integer>> QUERY =
      new ActionFilterBuilder<Pair<String, Integer>>() {
        @Override
        public Pair<String, Integer> added(Optional<Instant> start, Optional<Instant> end) {
          return new Pair<>(String.format("added between %s to %s", start, end), 0);
        }

        @Override
        public Pair<String, Integer> addedAgo(long offset) {
          return formatAgo("added", offset);
        }

        @Override
        public Pair<String, Integer> and(Stream<Pair<String, Integer>> filters) {
          return new Pair<>(
              filters
                  .map(p -> p.second() > 2 ? "(" + p.first() + ")" : p.first())
                  .collect(Collectors.joining(" and ")),
              2);
        }

        @Override
        public Pair<String, Integer> checked(Optional<Instant> start, Optional<Instant> end) {
          return new Pair<>(String.format("checked between %s to %s", start, end), 0);
        }

        @Override
        public Pair<String, Integer> checkedAgo(long offset) {
          return formatAgo("checked", offset);
        }

        @Override
        public Pair<String, Integer> external(Optional<Instant> start, Optional<Instant> end) {
          return new Pair<>(String.format("external between %s to %s", start, end), 0);
        }

        @Override
        public Pair<String, Integer> externalAgo(long offset) {
          return formatAgo("external", offset);
        }

        private Pair<String, Integer> formatAgo(String name, long offset) {
          final long reduced;
          final String units;
          if (offset % 86_400_000 == 0) {
            reduced = offset / 86_400_000;
            units = "days";
          } else if (offset % 3_600_000 == 0) {
            reduced = offset / 3_600_000;
            units = "hours";
          } else if (offset % 60_000 == 0) {
            reduced = offset / 60_000;
            units = "mins";
          } else if (offset % 10_000 == 0) {
            reduced = offset / 86400;
            units = "secs";
          } else {
            reduced = offset;
            units = "millis";
          }
          return new Pair<>(String.format("%s after %d%s", name, reduced, units), 1);
        }

        private Pair<String, Integer> formatSet(String name, String... items) {
          if (items.length == 1) {
            return new Pair<>(String.format("%s = %s", name, quote(items[0])), 1);
          } else {
            return new Pair<>(
                Stream.of(items)
                    .map(this::quote)
                    .collect(Collectors.joining(", ", name + " in (", ")")),
                1);
          }
        }

        @Override
        public Pair<String, Integer> fromFile(String... files) {
          return formatSet("file", files);
        }

        @Override
        public Pair<String, Integer> fromSourceLocation(Stream<SourceOliveLocation> locations) {
          final List<SourceOliveLocation> locationList = locations.collect(Collectors.toList());
          if (locationList.size() == 1) {
            return new Pair<>("source = " + locationList.get(0), 0);
          }
          return new Pair<>(
              locationList
                  .stream()
                  .map(Object::toString)
                  .collect(Collectors.joining(", ", "source in (", ")")),
              0);
        }

        @Override
        public Pair<String, Integer> ids(List<String> ids) {
          return ids.size() == 1
              ? new Pair<>(ids.get(0), 0)
              : new Pair<>(String.join(" or ", ids), 3);
        }

        @Override
        public Pair<String, Integer> isState(ActionState... states) {
          final String[] stateNames = new String[states.length];
          for (int i = 0; i < stateNames.length; i++) {
            stateNames[i] = states[i].name().toLowerCase();
          }
          Arrays.sort(stateNames);
          return formatSet("status", stateNames);
        }

        @Override
        public Pair<String, Integer> negate(Pair<String, Integer> filter) {
          return new Pair<>(
              "!" + (filter.second() > 1 ? "(" + filter.first() + ")" : filter.first()), 1);
        }

        @Override
        public Pair<String, Integer> or(Stream<Pair<String, Integer>> filters) {
          return new Pair<>(
              filters
                  .map(p -> p.second() > 3 ? "(" + p.first() + ")" : p.first())
                  .collect(Collectors.joining(" and ")),
              3);
        }

        private String quote(String item) {
          if (Parser.IDENTIFIER.matcher(item).matches()) {
            return item;
          } else {
            return "\"" + item.replace("\"", "\\\"") + "\"";
          }
        }

        @Override
        public Pair<String, Integer> statusChanged(Optional<Instant> start, Optional<Instant> end) {
          final ActionFilterStatusChanged result = new ActionFilterStatusChanged();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return new Pair<>(String.format("status_changed between %s to %s", start, end), 0);
        }

        @Override
        public Pair<String, Integer> statusChangedAgo(long offset) {
          return formatAgo("status_changed", offset);
        }

        @Override
        public Pair<String, Integer> tags(Stream<String> tags) {
          return formatSet("tag", tags.toArray(String[]::new));
        }

        @Override
        public Pair<String, Integer> textSearch(Pattern pattern) {

          return new Pair<>(
              String.format(
                  "text ~ /%s/%s",
                  pattern.pattern(), (pattern.flags() & Pattern.CASE_INSENSITIVE) == 0 ? "" : "i"),
              0);
        }

        @Override
        public Pair<String, Integer> type(String... types) {
          return formatSet("type", types);
        }
      };
  /**
   * Check that an action was last added in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F added(Optional<Instant> start, Optional<Instant> end);

  /**
   * Check that an action was added in a recent range
   *
   * @param offset the number of milliseconds ago
   */
  default F addedAgo(long offset) {
    return added(Optional.of(Instant.now().minusMillis(offset)), Optional.empty());
  }

  /** Check that all of the filters match */
  F and(Stream<F> filters);

  /**
   * Check that an action was last checked in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F checked(Optional<Instant> start, Optional<Instant> end);

  /**
   * Check that an action was checked by the scheduler in a recent range
   *
   * @param offset the number of milliseconds ago
   */
  default F checkedAgo(long offset) {
    return checked(Optional.of(Instant.now().minusMillis(offset)), Optional.empty());
  }

  /**
   * Check that an action's external timestamp is in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F external(Optional<Instant> start, Optional<Instant> end);

  /**
   * Check that an action has an external timestamp in a recent range
   *
   * @param offset the number of milliseconds ago
   */
  default F externalAgo(long offset) {
    return external(Optional.of(Instant.now().minusMillis(offset)), Optional.empty());
  }

  /**
   * Checks that an action was generated in a particular file
   *
   * @param files the names of the files
   */
  F fromFile(String... files);

  /**
   * Checks that an action was generated in a particular source location
   *
   * @param locations the source locations
   */
  F fromSourceLocation(Stream<SourceOliveLocation> locations);

  /**
   * Get actions by unique ID.
   *
   * @param ids the allowed identifiers
   */
  F ids(List<String> ids);

  /**
   * Checks that an action is in one of the specified actions states
   *
   * @param states the permitted states
   */
  F isState(ActionState... states);

  F negate(F filter);

  /** Check that any of the filters match */
  F or(Stream<F> filters);

  /**
   * Check that an action's last status change was in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F statusChanged(Optional<Instant> start, Optional<Instant> end);

  /**
   * Check that an action was added in a recent range
   *
   * @param offset the number of milliseconds ago
   */
  default F statusChangedAgo(long offset) {
    return statusChanged(Optional.of(Instant.now().minusMillis(offset)), Optional.empty());
  }

  /**
   * Check that an action has one of the listed tags attached
   *
   * @param tags the set of tags
   */
  F tags(Stream<String> tags);

  /**
   * Check that an action matches the regular expression provided
   *
   * @param pattern the pattern
   */
  F textSearch(Pattern pattern);

  /** Check that an action has one of the types specified */
  F type(String... types);
}
