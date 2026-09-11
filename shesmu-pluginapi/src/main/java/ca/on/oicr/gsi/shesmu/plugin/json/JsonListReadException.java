package ca.on.oicr.gsi.shesmu.plugin.json;

import tools.jackson.databind.JavaType;

/**
 * Indicates that a streaming JSON array could not be read to completion, recording how much of the
 * array was decoded first
 *
 * <p>A response that is cut short mid-body surfaces from the JDK's HTTP client as an uninformative
 * <code>closed</code>. The progress recorded here is what distinguishes a server that stopped
 * early, or a proxy that timed out, from genuinely malformed JSON: an offset that is identical on
 * every failure implicates a fixed buffer somewhere, while one that moves implicates a timeout.
 */
public final class JsonListReadException extends RuntimeException {
  private final long byteOffset;
  private final long recordsRead;

  JsonListReadException(JavaType targetType, long recordsRead, long byteOffset, Throwable cause) {
    super(
        String.format(
            "Failed reading JSON array of %s after %d record(s) and %s",
            targetType.getRawClass().getSimpleName(),
            recordsRead,
            byteOffset < 0 ? "an unknown number of bytes" : byteOffset + " byte(s)"),
        cause);
    this.byteOffset = byteOffset;
    this.recordsRead = recordsRead;
  }

  /** The number of bytes consumed before the failure, or -1 if that could not be determined */
  public long byteOffset() {
    return byteOffset;
  }

  /** The number of array elements successfully decoded before the failure */
  public long recordsRead() {
    return recordsRead;
  }
}
