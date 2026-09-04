package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Alert;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Check that the source locations of an alert survive serialisation
 *
 * <p>{@link SourceLocation} has no bean getters, so if {@link
 * SourceLocation.SourceLocationSerializer} is not registered on the mapper, Jackson writes each
 * location as an empty object and the alerts dashboard cannot render them. That happened silently
 * once already, during the Jackson 3 migration, so it is checked here.
 */
public class AlertSerializationTest {

  private static final SourceLocationLinker LINKER =
      (path, line, column, hash) -> Stream.of("https://git.example.com/" + path + "#L" + line);

  public AlertSerializationTest() {}

  private ActionProcessor.Alert alert() {
    final Alert alert = new ActionProcessor.Alert("0123456789abcdef");
    alert.setStartsAt(Instant.EPOCH);
    alert.expiresIn(3600);
    alert.getLocations().add(new SourceLocation("olives/test.shesmu", 12, 3, "abc123"));
    return alert;
  }

  /** An alert must carry the fields the front end needs, not an empty object */
  @Test
  public void testLocationsAreSerialized() {
    final String json =
        ActionProcessor.writerFor(RuntimeSupport.MAPPER, SourceLocationLinker.EMPTY)
            .writeValueAsString(alert());
    Assertions.assertTrue(
        json.contains("\"file\":\"olives/test.shesmu\""),
        "Alert is missing the location file: " + json);
    Assertions.assertTrue(
        json.contains("\"line\":12"), "Alert is missing the location line: " + json);
    Assertions.assertTrue(
        json.contains("\"column\":3"), "Alert is missing the location column: " + json);
    Assertions.assertTrue(
        json.contains("\"hash\":\"abc123\""), "Alert is missing the location hash: " + json);
  }

  /** A linker that has a URL for the olive must have it included */
  @Test
  public void testLocationUrlIsSerialized() {
    final String json =
        ActionProcessor.writerFor(RuntimeSupport.MAPPER, LINKER).writeValueAsString(alert());
    Assertions.assertTrue(
        json.contains("\"url\":\"https://git.example.com/olives/test.shesmu#L12\""),
        "Alert is missing the location URL: " + json);
  }

  /**
   * Writing a source location without a linker must still produce a location the front end can
   * render; only the URL is lost
   */
  @Test
  public void testWritingWithoutALinkerOmitsTheUrl() {
    final String json = RuntimeSupport.MAPPER.writeValueAsString(alert());
    Assertions.assertTrue(
        json.contains("\"file\":\"olives/test.shesmu\""),
        "Alert is missing the location file: " + json);
    Assertions.assertFalse(
        json.contains("\"url\""), "Alert has a location URL with no linker: " + json);
  }

  /**
   * A writer from {@link ActionProcessor#writerFor(ObjectMapper, SourceLocationLinker)} must write
   * usable locations even if the mapper it was given has no serializer registered, since that is
   * the mistake that broke the alerts dashboard
   */
  @Test
  public void testWriterForInstallsTheSerializer() {
    final String json =
        ActionProcessor.writerFor(JsonMapper.builder().build(), LINKER).writeValueAsString(alert());
    Assertions.assertTrue(
        json.contains("\"file\":\"olives/test.shesmu\""),
        "Alert is missing the location file: " + json);
    Assertions.assertTrue(
        json.contains("\"url\":\"https://git.example.com/olives/test.shesmu#L12\""),
        "Alert is missing the location URL: " + json);
  }

  /** An alert with no locations is not affected by any of this */
  @Test
  public void testAlertWithoutLocations() {
    final Alert alert = new ActionProcessor.Alert("0123456789abcdef");
    alert.setStartsAt(Instant.EPOCH);
    alert.expiresIn(3600);
    Assertions.assertTrue(
        ActionProcessor.writerFor(RuntimeSupport.MAPPER, LINKER)
            .writeValueAsString(alert)
            .contains("\"locations\":[]"));
  }
}
