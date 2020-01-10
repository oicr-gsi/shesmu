package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface FilterBuilder<F> {

  F negate(F filter);

  /**
   * Check that an action was last added in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F added(Optional<Instant> start, Optional<Instant> end);

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
   * Check that an action's external timestamp is in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  F external(Optional<Instant> start, Optional<Instant> end);

  /**
   * Checks that an action was generated in a particular source location
   *
   * @param locations the source locations
   */
  F fromSourceLocation(Stream<Predicate<SourceLocation>> locations);

  /**
   * Checks that an action was generated in a particular file
   *
   * @param files the names of the files
   */
  F fromFile(String... files);

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
