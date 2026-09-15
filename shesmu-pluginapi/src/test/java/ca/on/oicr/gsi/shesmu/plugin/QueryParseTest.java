package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.BaseRangeActionFilter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class QueryParseTest {

  /**
   * Parse a query and print it back out, so that a query which the printer emits can be checked
   * against the parser that has to read it back.
   */
  private String roundTrip(String query) {
    final StringBuilder errors = new StringBuilder();
    final Optional<ActionFilter> filter =
        ActionFilter.parseQuery(
            query,
            name -> Optional.empty(),
            (line, column, message) ->
                errors.append(line).append(':').append(column).append(": ").append(message));
    return filter
        .map(f -> f.convert(ActionFilterBuilder.QUERY).first())
        .orElseGet(() -> "FAILED: " + errors);
  }

  /**
   * {@code status} is a prefix of {@code status_changed}, so the keyword boundary has to reject the
   * underscore or the shorter keyword wins and the rest of the variable name is left as junk.
   */
  @Test
  public void testStatusChangedIsNotParsedAsStatus() {
    Assertions.assertEquals("status_changed last 2hours", roundTrip("status_changed last 2hours"));
  }

  @Test
  public void testStatusStillParses() {
    Assertions.assertEquals("status = inflight", roundTrip("status = inflight"));
  }

  @Test
  public void testTemporalVariablesRoundTrip() {
    for (final String variable :
        new String[] {"generated", "checked", "created", "external", "status_changed"}) {
      final String query = variable + " last 2hours";
      Assertions.assertEquals(query, roundTrip(query));
    }
  }

  /**
   * {@link BaseRangeActionFilter} reads its endpoints as epoch milliseconds, so the parser has to
   * write them in the same units or an absolute date lands in 1970.
   */
  @Test
  public void testAbsoluteTimeIsInMilliseconds() {
    final long expected =
        LocalDateTime.of(2024, 1, 1, 12, 34, 56)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    for (final String variable :
        new String[] {"generated", "checked", "created", "external", "status_changed"}) {
      final Optional<ActionFilter> filter =
          ActionFilter.parseQuery(
              variable + " after 2024-01-01T12:34:56",
              name -> Optional.empty(),
              (line, column, message) -> Assertions.fail(line + ":" + column + ": " + message));
      Assertions.assertEquals(
          expected,
          filter
              .map(f -> ((BaseRangeActionFilter) f).getStart())
              .orElseThrow(() -> new AssertionError("failed to parse " + variable)));
    }
  }
}
