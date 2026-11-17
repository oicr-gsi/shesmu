package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file that may be on the local file system or at a remote URI
 *
 * <p>The Shesmu type is <code>PATH{path} | URI{uri}</code>. Use this rather than a <code>path
 * </code> for files that might be remote (ex. gs://bucket/file), since {@link Path#of(String,
 * String...)} collapses the "//" after the scheme.
 */
public final class PathOrUri {
  /** The descriptor for {@link #TYPE} */
  public static final String DESCRIPTOR = "u2PATH$t1pURI$t1r";

  /** The Shesmu type: <code>PATH{path} | URI{uri}</code> */
  public static final Imyhat TYPE =
      Imyhat.algebraicTuple("PATH", Imyhat.PATH).unify(Imyhat.algebraicTuple("URI", Imyhat.URI));

  // DOTALL, since an object name may contain any character, including a newline
  private static final Pattern SCHEME =
      Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/]*)(.*)$", Pattern.DOTALL);

  /**
   * Convert a string to a path or URI
   *
   * <p>A string with a scheme (ex. gs://bucket/file) becomes <code>URI</code>. Everything after the
   * host is taken literally as the file or object name, so characters such as spaces, %, ?, and #
   * are escaped rather than interpreted, and {@link #format(AlgebraicValue)} returns the original
   * string. Anything else, including a string with a scheme that cannot be made into a URI (ex.
   * gs://), becomes <code>PATH</code>, exactly as {@link Path#of(String, String...)} parses it.
   *
   * @param input the string to convert
   * @return the value as <code>PATH{path} | URI{uri}</code>
   */
  public static AlgebraicValue parse(String input) {
    final Matcher matcher = SCHEME.matcher(input);
    if (matcher.matches()) {
      try {
        return new AlgebraicValue(
            "URI", new URI(matcher.group(1), matcher.group(2), matcher.group(3), null, null));
      } catch (URISyntaxException e) {
        // Not a valid URI, so treat it as a path, as before remote files were supported
      }
    }
    return new AlgebraicValue("PATH", Path.of(input));
  }

  /**
   * Convert a path or URI back to a string
   *
   * <p>This is the inverse of {@link #parse(String)}: paths are returned as-is and URIs keep their
   * scheme (ex. gs://bucket/file) with the name unescaped.
   *
   * @param value a <code>PATH{path} | URI{uri}</code> value
   * @return the string form
   */
  public static String format(AlgebraicValue value) {
    if (value.get(0) instanceof URI uri && uri.getScheme() != null && !uri.isOpaque()) {
      final String authority = uri.getRawAuthority();
      return uri.getScheme() + "://" + (authority == null ? "" : authority) + uri.getPath();
    }
    return value.get(0).toString();
  }

  private PathOrUri() {}
}
