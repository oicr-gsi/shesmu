package ca.on.oicr.gsi.shesmu.plugin.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class JsonListBodyHandlerTest {

  /** A record in the arrays used by these tests */
  public static class Item {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  /** Fails part way through, the way a response that is cut short mid-body does */
  private static final class TruncatingInputStream extends FilterInputStream {
    private boolean closed;
    private int remaining;

    private TruncatingInputStream(byte[] content, int truncateAfter) {
      super(new ByteArrayInputStream(content));
      this.remaining = truncateAfter;
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        throw new IOException("closed");
      }
      remaining--;
      return super.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (remaining <= 0) {
        throw new IOException("closed");
      }
      final int result = super.read(buffer, offset, Math.min(length, remaining));
      if (result > 0) {
        remaining -= result;
      }
      return result;
    }
  }

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private static String array(int items) {
    final StringBuilder output = new StringBuilder("[");
    for (int index = 0; index < items; index++) {
      if (index > 0) {
        output.append(",");
      }
      output.append("{\"name\":\"item-").append(index).append("\"}");
    }
    return output.append("]").toString();
  }

  private static Stream<Item> streamOf(InputStream input) {
    // The type witness is needed because nothing in the arguments pins the element type down
    return JsonListBodyHandler.<Item>toSupplierOfType(
            MAPPER, input, MAPPER.getTypeFactory().constructType(Item.class))
        .get();
  }

  @Test
  public void testCompleteArrayIsRead() {
    try (final Stream<Item> stream =
        streamOf(new ByteArrayInputStream(array(50).getBytes(StandardCharsets.UTF_8)))) {
      final List<String> names = stream.map(Item::getName).toList();
      assertEquals(50, names.size());
      assertEquals("item-0", names.get(0));
      assertEquals("item-49", names.get(49));
    }
  }

  @Test
  public void testNullBodyIsEmpty() {
    try (final Stream<Item> stream =
        streamOf(new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8)))) {
      assertEquals(0, stream.count());
    }
  }

  /** An empty body used to produce a bare NullPointerException from the switch on the token */
  @Test
  public void testEmptyBodyIsReported() {
    final IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> streamOf(new ByteArrayInputStream(new byte[0])));
    assertTrue(error.getMessage().contains("empty response body"), error.getMessage());
  }

  /**
   * A proxy error page is not JSON at all, and should not be described as if it were broken JSON
   */
  @Test
  public void testNonJsonBodyIsReported() {
    final TruncatingInputStream input =
        new TruncatingInputStream(
            "<html><body>502 Bad Gateway</body></html>".getBytes(StandardCharsets.UTF_8),
            Integer.MAX_VALUE);
    final IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> streamOf(input));
    assertTrue(error.getMessage().contains("was not JSON"), error.getMessage());
    assertTrue(error.getMessage().contains("Item"), error.getMessage());
    assertTrue(input.closed, "the response body must be closed when the body is not JSON");
  }

  /** The reported token used to be the one after the offending one */
  @Test
  public void testNonArrayBodyIsReported() {
    final IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> streamOf(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));
    assertTrue(error.getMessage().contains("Item"), error.getMessage());
    assertTrue(error.getMessage().contains("START_OBJECT"), error.getMessage());
  }

  /**
   * The case from the miso-stage failure: the body dies part way through, and the resulting error
   * has to say how far it got
   */
  @Test
  public void testTruncatedArrayReportsProgress() {
    final byte[] content = array(500).getBytes(StandardCharsets.UTF_8);
    final TruncatingInputStream input = new TruncatingInputStream(content, content.length / 2);
    final List<Item> consumed = new ArrayList<>();
    final JsonListReadException error =
        assertThrows(
            JsonListReadException.class,
            () -> {
              try (final Stream<Item> stream = streamOf(input)) {
                stream.forEach(consumed::add);
              }
            });
    assertTrue(error.recordsRead() > 0, "should have decoded some records before failing");
    assertTrue(error.recordsRead() < 500, "should not have decoded the whole array");
    assertEquals(consumed.size(), error.recordsRead());
    assertTrue(error.byteOffset() > 0, "should report where it stopped");
    assertTrue(
        error.getMessage().contains("Item") && error.getMessage().contains("record"),
        error.getMessage());
    assertTrue(input.closed, "the response body must be closed when reading fails");
  }

  /**
   * A body that is already dead fails while the parser is being created, before any stream exists
   * for the caller to close, so the body has to be released here
   */
  @Test
  public void testDeadBodyIsClosed() {
    final TruncatingInputStream input =
        new TruncatingInputStream(array(500).getBytes(StandardCharsets.UTF_8), 0);
    assertThrows(RuntimeException.class, () -> streamOf(input));
    assertTrue(input.closed, "the response body must be closed when the parser cannot be created");
  }

  /** Abandoning the stream early has to release the connection too */
  @Test
  public void testEarlyCloseClosesBody() {
    final byte[] content = array(500).getBytes(StandardCharsets.UTF_8);
    final TruncatingInputStream input = new TruncatingInputStream(content, content.length);
    try (final Stream<Item> stream = streamOf(input)) {
      assertEquals(10, stream.limit(10).count());
    }
    assertTrue(input.closed, "the response body must be closed when the stream is abandoned");
  }

  /** An exception from downstream must not be reported as a JSON reading failure */
  @Test
  public void testDownstreamFailureIsNotWrapped() {
    final ByteArrayInputStream input =
        new ByteArrayInputStream(array(50).getBytes(StandardCharsets.UTF_8));
    assertThrows(
        UncheckedIOException.class,
        () -> {
          try (final Stream<Item> stream = streamOf(input)) {
            stream.forEach(
                item -> {
                  throw new UncheckedIOException(new IOException("downstream"));
                });
          }
        });
  }

  /** The cause chain, not just the outermost message, has to reach the operator */
  @Test
  public void testCauseChainIsDescribed() {
    assertEquals(
        "IOException: closed caused by IOException: chunked transfer encoding caused by"
            + " EOFException: EOF reached while reading",
        Utils.describeCauseChain(
            new IOException(
                "closed",
                new IOException(
                    "chunked transfer encoding",
                    new java.io.EOFException("EOF reached while reading")))));
  }

  /**
   * Jackson's messages carry a newline and a source location, and this text goes into a single log
   * line and into the user interface
   */
  @Test
  public void testMultiLineCauseMessageIsFlattened() {
    final String described =
        Utils.describeCauseChain(
            assertThrows(
                RuntimeException.class,
                () -> MAPPER.readValue("{oops", JsonListBodyHandlerTest.Item.class)));
    assertTrue(described.lines().count() == 1, described);
    assertTrue(described.contains("was expecting double-quote"), described);
  }

  /** A single enormous message must not swamp the rest of the chain */
  @Test
  public void testLongCauseMessageIsAbbreviated() {
    final String described =
        Utils.describeCauseChain(
            new IllegalStateException(
                "x".repeat(5000), new IllegalStateException("the real cause")));
    assertTrue(described.length() < 400, "was " + described.length() + " characters");
    assertTrue(described.contains("..."), described);
    assertTrue(described.endsWith("caused by IllegalStateException: the real cause"), described);
  }

  /** An exception with no message contributes only its name */
  @Test
  public void testMissingCauseMessageIsOmitted() {
    assertEquals(
        "IllegalStateException caused by IllegalStateException: inner",
        Utils.describeCauseChain(
            new IllegalStateException((String) null, new IllegalStateException("inner"))));
  }

  /** A cyclic cause chain must not loop forever */
  @Test
  public void testCyclicCauseChainTerminates() {
    final IllegalStateException first = new IllegalStateException("first");
    final IllegalStateException second = new IllegalStateException("second");
    first.initCause(second);
    second.initCause(first);
    assertEquals(
        "IllegalStateException: first caused by IllegalStateException: second",
        Utils.describeCauseChain(first));
  }
}
