package ca.on.oicr.gsi.shesmu.compiler;

/**
 * Handle a parsing error at a particular location
 *
 * <p>The parser will spuriously emit these, so only the “best” one should be shown to the user.
 */
public interface ErrorConsumer {
  public void raise(int line, int column, String errorMessage);
}
