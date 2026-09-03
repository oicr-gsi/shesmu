package ca.on.oicr.gsi.shesmu.pinery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class LazilyTest {

  /**
   * The point of the whole exercise: the request must not be made until the preceding batch has
   * been read
   */
  @Test
  public void testRequestIsDeferredUntilRead() {
    final var events = new ArrayList<String>();
    final var combined =
        Stream.concat(
            Stream.of("a1", "a2").peek(events::add),
            PinerySource.lazily(
                () -> {
                  events.add("requested");
                  return Stream.of("b1").peek(events::add);
                }));
    assertEquals(List.of(), events, "nothing may happen before the stream is read");
    combined.forEach(item -> {});
    assertEquals(List.of("a1", "a2", "requested", "b1"), events);
  }

  /** Reading only part of the first batch must not trigger the request either */
  @Test
  public void testPartialReadDoesNotRequest() {
    final var requested = new boolean[1];
    try (final var combined =
        Stream.concat(
            Stream.of("a1", "a2", "a3"),
            PinerySource.lazily(
                () -> {
                  requested[0] = true;
                  return Stream.of("b1");
                }))) {
      assertEquals(2, combined.limit(2).count());
    }
    assertFalse(requested[0], "the second batch must not be requested if it is never reached");
  }

  /** A failure while reading the first batch must not leave the second one requested */
  @Test
  public void testFailureBeforeSecondBatchDoesNotRequest() {
    final var requested = new boolean[1];
    assertThrows(
        IllegalStateException.class,
        () -> {
          try (final var combined =
              Stream.concat(
                  Stream.of("a1", "a2")
                      .peek(
                          item -> {
                            throw new IllegalStateException("first batch died");
                          }),
                  PinerySource.lazily(
                      () -> {
                        requested[0] = true;
                        return Stream.of("b1");
                      }))) {
            combined.forEach(item -> {});
          }
        });
    assertFalse(requested[0]);
  }

  /** The deferred stream still has to be closed, or the connection leaks */
  @Test
  public void testDeferredStreamIsClosedOnFailure() {
    final var closed = new boolean[1];
    assertThrows(
        IllegalStateException.class,
        () -> {
          try (final var combined =
              Stream.concat(
                  Stream.of("a1"),
                  PinerySource.lazily(
                      () ->
                          Stream.of("b1", "b2")
                              .onClose(() -> closed[0] = true)
                              .peek(
                                  item -> {
                                    if (item.equals("b2")) {
                                      throw new IllegalStateException("second batch died");
                                    }
                                  })))) {
            combined.forEach(item -> {});
          }
        });
    assertTrue(closed[0], "the deferred stream must be closed when reading it fails");
  }

  @Test
  public void testDeferredStreamIsClosedOnSuccess() {
    final var closed = new boolean[1];
    try (final var combined =
        Stream.concat(
            Stream.of("a1"),
            PinerySource.lazily(() -> Stream.of("b1").onClose(() -> closed[0] = true)))) {
      assertEquals(2, combined.count());
    }
    assertTrue(closed[0]);
  }

  /** An IOException from the request has to survive as the cause, for the sake of the log */
  @Test
  public void testIoExceptionIsWrappedWithItsCause() {
    final var cause = new IOException("closed");
    final var error =
        assertThrows(
            UncheckedIOException.class,
            () ->
                Stream.concat(
                        Stream.of("a1"),
                        PinerySource.lazily(
                            () -> {
                              throw cause;
                            }))
                    .forEach(item -> {}));
    assertSame(cause, error.getCause());
  }

  /** Interruption must not be swallowed */
  @Test
  public void testInterruptionSetsTheInterruptFlag() {
    final var cause = new InterruptedException("stopped");
    final var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                Stream.concat(
                        Stream.of("a1"),
                        PinerySource.lazily(
                            () -> {
                              throw cause;
                            }))
                    .forEach(item -> {}));
    assertSame(cause, error.getCause());
    // Clear the flag so it cannot affect any test that runs afterwards on this thread
    assertTrue(Thread.interrupted(), "the interrupt flag must be restored");
  }
}
