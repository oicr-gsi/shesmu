package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build an implementation-specific action filter
 *
 * @param <F> the resulting filter type
 * @param <T> action state type
 * @param <S> string type
 * @param <I> time instant type
 * @param <O> time offset type
 */
public interface ActionFilterBuilder<F, T, S, I, O> {
  /** Do a "transformation" of the JSON representation to an exact copy. */
  ActionFilterBuilder<ActionFilter, ActionState, String, Instant, Long> JSON =
      new ActionFilterBuilder<>() {
        @Override
        public ActionFilter added(Optional<Instant> start, Optional<Instant> end) {
          final var result = new ActionFilterAdded();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter addedAgo(Long offset) {
          final var result = new ActionFilterAddedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter and(Stream<ActionFilter> filters) {
          final var result = new ActionFilterAnd();
          result.setFilters(filters.toArray(ActionFilter[]::new));
          return result;
        }

        @Override
        public ActionFilter checked(Optional<Instant> start, Optional<Instant> end) {
          final var result = new ActionFilterChecked();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter checkedAgo(Long offset) {
          final var result = new ActionFilterCheckedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter created(Optional<Instant> start, Optional<Instant> end) {
          final var result = new ActionFilterCreated();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter createdAgo(Long offset) {
          final var result = new ActionFilterCreatedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter external(Optional<Instant> start, Optional<Instant> end) {
          final var result = new ActionFilterExternal();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter externalAgo(Long offset) {
          final var result = new ActionFilterExternalAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter fromFile(Stream<String> files) {
          final var result = new ActionFilterSourceFile();
          result.setFiles(files.toArray(String[]::new));
          return result;
        }

        @Override
        public ActionFilter fromJson(ActionFilter actionFilter) {
          return actionFilter;
        }

        @Override
        public ActionFilter fromSourceLocation(Stream<SourceOliveLocation> locations) {
          final var result = new ActionFilterSourceLocation();
          result.setLocations(
              locations.map(SourceOliveLocation::new).toArray(SourceOliveLocation[]::new));
          return result;
        }

        @Override
        public ActionFilter ids(List<String> ids) {
          final var result = new ActionFilterIds();
          result.setIds(ids);
          return result;
        }

        @Override
        public ActionFilter isState(Stream<ActionState> states) {
          final var result = new ActionFilterStatus();
          result.setState(states.toArray(ActionState[]::new));
          return result;
        }

        @Override
        public ActionFilter negate(ActionFilter filter) {
          // In the real use of the filter, the negation produces a new filter which returns the
          // logical not of the provided filter. In the case of JSON objects, the negation is a
          // field in the object. So, we copy the object and then negate it. Since it may already
          // have been negated, we flip the negation bit rather than setting it.
          final var copy = filter.convert(this);
          copy.setNegate(!copy.isNegate());
          return copy;
        }

        @Override
        public ActionFilter or(Stream<ActionFilter> filters) {
          final var result = new ActionFilterOr();
          result.setFilters(filters.toArray(ActionFilter[]::new));
          return result;
        }

        @Override
        public ActionFilter statusChanged(Optional<Instant> start, Optional<Instant> end) {
          final var result = new ActionFilterStatusChanged();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public ActionFilter statusChangedAgo(Long offset) {
          final var result = new ActionFilterStatusChangedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public ActionFilter tag(Pattern pattern) {
          final var result = new ActionFilterTagRegex();
          result.setPattern(pattern.pattern());
          result.setMatchCase((pattern.flags() & Pattern.CASE_INSENSITIVE) == 0);
          return result;
        }

        @Override
        public ActionFilter tags(Stream<String> tags) {
          final var result = new ActionFilterTag();
          result.setTags(tags.toArray(String[]::new));
          return result;
        }

        @Override
        public ActionFilter textSearch(Pattern pattern) {
          final var result = new ActionFilterRegex();
          result.setPattern(pattern.pattern());
          result.setMatchCase((pattern.flags() & Pattern.CASE_INSENSITIVE) == 0);
          return result;
        }

        @Override
        public ActionFilter textSearch(String text, boolean matchCase) {
          final var result = new ActionFilterRegex();
          result.setPattern(Pattern.quote(text));
          result.setMatchCase(matchCase);
          return result;
        }

        @Override
        public ActionFilter type(Stream<String> types) {
          final var result = new ActionFilterType();
          result.setTypes(types.toArray(String[]::new));
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
  ActionFilterBuilder<Pair<String, Integer>, ActionState, String, Instant, Long> QUERY =
      new ActionFilterBuilder<>() {
        private final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

        @Override
        public Pair<String, Integer> added(Optional<Instant> start, Optional<Instant> end) {
          return range("generated", start, end);
        }

        @Override
        public Pair<String, Integer> addedAgo(Long offset) {
          return formatAgo("generated", offset);
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
          return range("checked", start, end);
        }

        @Override
        public Pair<String, Integer> checkedAgo(Long offset) {
          return formatAgo("checked", offset);
        }

        @Override
        public Pair<String, Integer> created(Optional<Instant> start, Optional<Instant> end) {
          return range("created", start, end);
        }

        @Override
        public Pair<String, Integer> createdAgo(Long offset) {
          return formatAgo("created", offset);
        }

        @Override
        public Pair<String, Integer> external(Optional<Instant> start, Optional<Instant> end) {
          return range("external", start, end);
        }

        @Override
        public Pair<String, Integer> externalAgo(Long offset) {
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
          } else if (offset % 1000 == 0) {
            reduced = offset / 1000;
            units = "secs";
          } else {
            reduced = offset;
            units = "millis";
          }
          return new Pair<>(String.format("%s last %d%s", name, reduced, units), 0);
        }

        private Pair<String, Integer> formatSet(String name, Stream<String> stream) {
          final var items = stream.collect(Collectors.toList());
          if (items.size() == 1) {
            return new Pair<>(String.format("%s = %s", name, quote(items.get(0))), 0);
          } else {
            return new Pair<>(
                items.stream()
                    .map(this::quote)
                    .collect(Collectors.joining(", ", name + " in (", ")")),
                0);
          }
        }

        @Override
        public Pair<String, Integer> fromFile(Stream<String> files) {
          return formatSet("file", files);
        }

        @Override
        public Pair<String, Integer> fromJson(ActionFilter actionFilter) {
          return actionFilter.convert(this);
        }

        @Override
        public Pair<String, Integer> fromSourceLocation(Stream<SourceOliveLocation> locations) {
          final var locationList = locations.collect(Collectors.toList());
          if (locationList.size() == 1) {
            return new Pair<>("source = " + locationList.get(0), 0);
          }
          return new Pair<>(
              locationList.stream()
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
        public Pair<String, Integer> isState(Stream<ActionState> states) {
          return formatSet("status", states.map(s -> s.name().toLowerCase()).distinct().sorted());
        }

        @Override
        public Pair<String, Integer> negate(Pair<String, Integer> filter) {
          return new Pair<>(
              "not " + (filter.second() > 1 ? "(" + filter.first() + ")" : filter.first()), 1);
        }

        @Override
        public Pair<String, Integer> or(Stream<Pair<String, Integer>> filters) {
          return new Pair<>(
              filters
                  .map(p -> p.second() > 3 ? "(" + p.first() + ")" : p.first())
                  .collect(Collectors.joining(" or ")),
              3);
        }

        private String quote(String item) {
          if (Parser.IDENTIFIER.matcher(item).matches()) {
            return item;
          } else {
            return "\"" + item.replace("\"", "\\\"") + "\"";
          }
        }

        private Pair<String, Integer> range(
            String name, Optional<Instant> start, Optional<Instant> end) {
          if (start.isPresent() && end.isPresent()) {
            return new Pair<>(
                String.format(
                    "%s between %s to %s",
                    name,
                    FORMATTER.format(start.get().truncatedTo(ChronoUnit.SECONDS)),
                    FORMATTER.format(end.get().truncatedTo(ChronoUnit.SECONDS))),
                0);
          } else if (start.isPresent()) {
            return new Pair<>(
                String.format(
                    "%s after %s",
                    name, FORMATTER.format(start.get().truncatedTo(ChronoUnit.SECONDS))),
                0);
          } else if (end.isPresent()) {
            return new Pair<>(
                String.format(
                    "%s before %s",
                    name, FORMATTER.format(end.get().truncatedTo(ChronoUnit.SECONDS))),
                0);
          } else {
            throw new IllegalStateException();
          }
        }

        @Override
        public Pair<String, Integer> statusChanged(Optional<Instant> start, Optional<Instant> end) {
          return range("status_changed", start, end);
        }

        @Override
        public Pair<String, Integer> statusChangedAgo(Long offset) {
          return formatAgo("status_changed", offset);
        }

        @Override
        public Pair<String, Integer> tag(Pattern pattern) {
          return new Pair<>(
              String.format(
                  "tag ~ /%s/%s",
                  pattern.pattern(), (pattern.flags() & Pattern.CASE_INSENSITIVE) == 0 ? "" : "i"),
              0);
        }

        @Override
        public Pair<String, Integer> tags(Stream<String> tags) {
          return formatSet("tag", tags);
        }

        @Override
        public Pair<String, Integer> textSearch(String text, boolean matchCase) {
          return new Pair<>("text = \"" + text.replace("\"", "\\\"") + "\"", 0);
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
        public Pair<String, Integer> type(Stream<String> types) {
          return formatSet("type", types);
        }
      };
  /**
   * Check that an action was last added in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   * @return the constructed filter
   */
  F added(Optional<I> start, Optional<I> end);

  /**
   * Check that an action was added in a recent range
   *
   * @param offset the number of milliseconds ago
   * @return the constructed filter
   */
  F addedAgo(O offset);

  /**
   * Check that all of the filters match
   *
   * @param filters the filters to match
   * @return the constructed filter
   */
  F and(Stream<F> filters);

  /**
   * Check that an action was last checked in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   * @return the constructed filter
   */
  F checked(Optional<I> start, Optional<I> end);

  /**
   * Check that an action was checked by the scheduler in a recent range
   *
   * @param offset the number of milliseconds ago
   * @return the constructed filter
   */
  F checkedAgo(O offset);

  /**
   * Check that an action was first created by an olive in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   * @return the constructed filter
   */
  F created(Optional<I> start, Optional<I> end);

  /**
   * Check that an action was first created by an olive in a recent range
   *
   * @param offset the number of milliseconds ago
   * @return the constructed filter
   */
  F createdAgo(O offset);

  /**
   * Check that an action's external timestamp is in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   * @return the constructed filter
   */
  F external(Optional<I> start, Optional<I> end);

  /**
   * Check that an action has an external timestamp in a recent range
   *
   * @param offset the number of milliseconds ago
   * @return the constructed filter
   */
  F externalAgo(O offset);

  /**
   * Checks that an action was generated in a particular file
   *
   * @param files the names of the files
   * @return the constructed filter
   */
  F fromFile(Stream<S> files);

  /**
   * Converts a filter in the JSON format used by the front-end to an implementation-specific
   * equivalent
   *
   * @param actionFilter the filter to convert
   * @return the converted filter
   */
  F fromJson(ActionFilter actionFilter);

  /**
   * Checks that an action was generated in a particular source location
   *
   * @param locations the source locations
   * @return the constructed filter
   */
  F fromSourceLocation(Stream<SourceOliveLocation> locations);

  /**
   * Get actions by unique ID.
   *
   * @param ids the allowed identifiers
   * @return the constructed filter
   */
  F ids(List<S> ids);

  /**
   * Checks that an action is in one of the specified actions states
   *
   * @param states the permitted states
   * @return the constructed filter
   */
  F isState(Stream<T> states);

  /**
   * Inverts the sense of a filter
   *
   * @param filter the filter to invert
   * @return the inverted filter
   */
  F negate(F filter);

  /**
   * Check that any of the filters match
   *
   * @param filters the filters to match
   * @return the constructed filter
   */
  F or(Stream<F> filters);

  /**
   * Check that an action's last status change was in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   * @return the constructed filter
   */
  F statusChanged(Optional<I> start, Optional<I> end);

  /**
   * Check that an action was added in a recent range
   *
   * @param offset the number of milliseconds ago
   * @return the constructed filter
   */
  F statusChangedAgo(O offset);

  /**
   * Check that an action has a tag matching the provided regular expression
   *
   * @param pattern the pattern
   * @return the constructed filter
   */
  F tag(Pattern pattern);
  /**
   * Check that an action has one of the listed tags attached
   *
   * @param tags the set of tags
   * @return the constructed filter
   */
  F tags(Stream<S> tags);

  /**
   * Check that an action matches the regular expression provided
   *
   * @param pattern the pattern
   * @return the constructed filter
   */
  F textSearch(Pattern pattern);

  /**
   * Check that an action matches the text provided
   *
   * @param text the text
   * @param matchCase whether the match should be case-sensitive
   * @return the constructed filter
   */
  F textSearch(S text, boolean matchCase);

  /**
   * Check that an action has one of the types specified
   *
   * @param types the action types to match
   * @return the constructed filter
   */
  F type(Stream<S> types);
}
