package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface FilterBuilder<F> {
  /** Do a "transformation" of the JSON representation to an exact copy. */
  FilterBuilder<FilterJson> JSON =
      new FilterBuilder<FilterJson>() {
        @Override
        public FilterJson added(Optional<Instant> start, Optional<Instant> end) {
          final FilterAdded result = new FilterAdded();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return result;
        }

        @Override
        public FilterJson addedAgo(long offset) {
          final FilterAddedAgo result = new FilterAddedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public FilterJson and(Stream<FilterJson> filters) {
          final FilterAnd result = new FilterAnd();
          result.setFilters(filters.toArray(FilterJson[]::new));
          return result;
        }

        @Override
        public FilterJson checked(Optional<Instant> start, Optional<Instant> end) {
          final FilterChecked result = new FilterChecked();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return null;
        }

        @Override
        public FilterJson checkedAgo(long offset) {
          final FilterCheckedAgo result = new FilterCheckedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public FilterJson external(Optional<Instant> start, Optional<Instant> end) {
          final FilterExternal result = new FilterExternal();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return null;
        }

        @Override
        public FilterJson externalAgo(long offset) {
          final FilterExternalAgo result = new FilterExternalAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public FilterJson fromFile(String... files) {
          final FilterSourceFile result = new FilterSourceFile();
          result.setFiles(files);
          return result;
        }

        @Override
        public FilterJson fromSourceLocation(Stream<LocationJson> locations) {
          final FilterSourceLocation result = new FilterSourceLocation();
          result.setLocations(locations.map(LocationJson::new).toArray(LocationJson[]::new));
          return result;
        }

        @Override
        public FilterJson ids(List<String> ids) {
          final FilterIds result = new FilterIds();
          result.setIds(ids);
          return result;
        }

        @Override
        public FilterJson isState(ActionState... states) {
          final FilterStatus result = new FilterStatus();
          result.setState(states);
          return result;
        }

        @Override
        public FilterJson negate(FilterJson filter) {
          // In the real use of the filter, the negation produces a new filter which returns the
          // logical not of the provided filter. In the case of JSON objects, the negation is a
          // field in the object. So, we copy the object and then negate it. Since it may already
          // have been negated, we flip the negation bit rather than setting it.
          final FilterJson copy = filter.convert(this);
          copy.setNegate(!copy.isNegate());
          return copy;
        }

        @Override
        public FilterJson or(Stream<FilterJson> filters) {
          final FilterOr result = new FilterOr();
          result.setFilters(filters.toArray(FilterJson[]::new));
          return result;
        }

        @Override
        public FilterJson statusChanged(Optional<Instant> start, Optional<Instant> end) {
          final FilterStatusChanged result = new FilterStatusChanged();
          result.setStart(start.map(Instant::getEpochSecond).orElse(null));
          result.setEnd(end.map(Instant::getEpochSecond).orElse(null));
          return null;
        }

        @Override
        public FilterJson statusChangedAgo(long offset) {
          final FilterStatusChangedAgo result = new FilterStatusChangedAgo();
          result.setOffset(offset);
          return result;
        }

        @Override
        public FilterJson tags(Stream<String> tags) {
          final FilterTag result = new FilterTag();
          result.setTags(tags.toArray(String[]::new));
          return result;
        }

        @Override
        public FilterJson textSearch(Pattern pattern) {
          final FilterRegex result = new FilterRegex();
          result.setPattern(pattern.pattern());
          return result;
        }

        @Override
        public FilterJson type(String... types) {
          final FilterType result = new FilterType();
          result.setTypes(types);
          return result;
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
  F fromSourceLocation(Stream<LocationJson> locations);

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
