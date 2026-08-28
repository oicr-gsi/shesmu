package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;

/**
 * Jackson 3 turns {@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} on by default, and {@code
 * ObjectMapper.readTree(JsonParser)} applies it to everything remaining in the stream — not just
 * the value it read. Shesmu pulls input-format data out of a JSON array one element at a time, so
 * under that default the *first* element of any multi-element array fails with "Trailing token
 * (`JsonToken.START_OBJECT`) found after value".
 *
 * <p>This bit dev: every remote/local JSON input format with more than one record stopped loading.
 * These tests pin the streaming idiom used by {@code BaseInputFormatDefinition} so the regression
 * cannot come back silently.
 */
public class JsonStreamingTest {

  private static final String MULTI_ELEMENT_ARRAY =
      "[{\"a\":1,\"b\":\"x\"},{\"a\":2,\"b\":\"y\"},{\"a\":3,\"b\":\"z\"}]";

  /** The same reader {@code BaseInputFormatDefinition} uses to walk array elements. */
  private static final ObjectReader ELEMENT_READER =
      RuntimeSupport.MAPPER
          .readerFor(JsonNode.class)
          .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private static List<JsonNode> readArrayElements(String json) {
    try (final var parser =
        RuntimeSupport.MAPPER
            .tokenStreamFactory()
            .createParser(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {
      final List<JsonNode> results = new ArrayList<>();
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        throw new IllegalStateException("Expected an array");
      }
      while (parser.nextToken() != JsonToken.END_ARRAY) {
        results.add(ELEMENT_READER.readValue(parser));
      }
      if (parser.nextToken() != null) {
        throw new IllegalStateException("Junk at end of JSON document");
      }
      return results;
    }
  }

  /** Reading a multi-element array element-by-element must not trip FAIL_ON_TRAILING_TOKENS. */
  @Test
  public void streamingReadOfMultiElementArraySucceeds() {
    final var elements = readArrayElements(MULTI_ELEMENT_ARRAY);
    Assertions.assertEquals(3, elements.size());
    Assertions.assertEquals(1, elements.get(0).get("a").asInt());
    Assertions.assertEquals(2, elements.get(1).get("a").asInt());
    Assertions.assertEquals(3, elements.get(2).get("a").asInt());
  }

  /**
   * A single-element array works either way; this pins that the fix did not break the easy case.
   */
  @Test
  public void streamingReadOfSingleElementArraySucceeds() {
    Assertions.assertEquals(1, readArrayElements("[{\"a\":1}]").size());
    Assertions.assertEquals(0, readArrayElements("[]").size());
  }

  /**
   * Demonstrates the defect this guards against: the plain {@code readTree(parser)} that shipped
   * with the Jackson 3 rewrite fails on the first element of a multi-element array.
   *
   * <p>If a future Jackson release changes this default, this test starts failing and the
   * workaround in {@code BaseInputFormatDefinition} can be reconsidered.
   */
  @Test
  public void plainReadTreeStillFailsMidArray() {
    final var failure =
        Assertions.assertThrows(
            tools.jackson.databind.exc.MismatchedInputException.class,
            () -> {
              try (final var parser =
                  RuntimeSupport.MAPPER
                      .tokenStreamFactory()
                      .createParser(
                          new ByteArrayInputStream(
                              MULTI_ELEMENT_ARRAY.getBytes(StandardCharsets.UTF_8)))) {
                parser.nextToken(); // START_ARRAY
                parser.nextToken(); // first element
                RuntimeSupport.MAPPER.readTree(parser);
              }
            },
            "readTree(parser) mid-array is expected to fail under Jackson 3 defaults");
    Assertions.assertTrue(
        failure.getMessage().contains("FAIL_ON_TRAILING_TOKENS"),
        () -> "unexpected failure: " + failure.getMessage());
  }

  /** Whole-document reads must stay strict: trailing junk is still an error. */
  @Test
  public void wholeDocumentReadsRemainStrict() {
    Assertions.assertThrows(
        tools.jackson.core.JacksonException.class,
        () -> RuntimeSupport.MAPPER.readTree("{\"a\":1} {\"b\":2}"),
        "the shared mapper must still reject trailing content in a whole document");
  }
}
