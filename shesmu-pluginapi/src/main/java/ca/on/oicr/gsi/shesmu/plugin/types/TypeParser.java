package ca.on.oicr.gsi.shesmu.plugin.types;

/** A service for converting a string containing a type into a Shesmu type */
public interface TypeParser {
  /** The name of the type format in a human-friendly format. */
  String description();
  /**
   * A namespaced ID for the type format
   *
   * <p>This will be the name used in the REST API
   */
  String format();
  /**
   * Parse a type string provided by the user
   *
   * <p>If conversion fails, return {@link Imyhat#BAD}.
   */
  Imyhat parse(String type);
}
