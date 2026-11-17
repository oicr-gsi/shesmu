package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file that may be on the local file system or at a remote URI
 *
 * <p>The Shesmu type is <code>PATH{path} | URI{uri}</code>. Use this rather than a <code>path
 * </code> for values that might be remote (ex. gs://bucket/file), since {@link Path#of(String,
 * String...)} collapses the "//" after the scheme.
 */
public final class Location {
  /** The descriptor for {@link #TYPE} */
  public static final String DESCRIPTOR = "u2PATH$t1pURI$t1r";

  /** The Shesmu type: <code>PATH{path} | URI{uri}</code> */
  public static final Imyhat TYPE =
      Imyhat.algebraicTuple("PATH", Imyhat.PATH).unify(Imyhat.algebraicTuple("URI", Imyhat.URI));

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private static final Pattern SCHEME =
      Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)$");

  /**
   * Create a location from a string
   *
   * @param location the location; strings with a scheme (ex. gs://bucket/file) become <code>URI
   *     </code>, except file:// which becomes <code>PATH</code>; everything else is treated as a
   *     local path. Everything after the authority is taken literally (it is escaped, not decoded),
   *     so {@link #format(AlgebraicValue)} returns the original string.
   * @return the location as a <code>PATH{path} | URI{uri}</code> value
   * @throws IllegalArgumentException if the string has a scheme but cannot be made into a URI (ex.
   *     <code>gs://</code>)
   */
  public static AlgebraicValue parse(String location) {
    final Matcher matcher = SCHEME.matcher(location);
    if (!matcher.matches()) {
      return path(Path.of(location));
    }
    // Everything after the authority is a raw name (as in gs://bucket/name, where the object name
    // may contain any character), not a URI path that is already escaped. Escape all of it so that
    // characters like %, ?, and # are kept as part of the name and format() can restore it exactly.
    if (matcher.group(1).equalsIgnoreCase("file")) {
      return path(Path.of(matcher.group(3)));
    }
    try {
      return uri(
          new URI(matcher.group(1) + "://" + matcher.group(2) + escapePath(matcher.group(3))));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URI: " + location, e);
    }
  }

  /**
   * Create a location from a string, falling back to a local path if it is not a valid URI
   *
   * <p>This is meant for plugins that read locations from an external system: a single malformed
   * record (ex. <code>gs://</code>) becomes a <code>PATH</code>, as {@link Path#of(String,
   * String...)} would have produced, rather than throwing and stopping every record from loading.
   *
   * @param location the location
   * @return the location as a <code>PATH{path} | URI{uri}</code> value
   * @see #parse(String)
   */
  public static AlgebraicValue parseOrPath(String location) {
    try {
      return parse(location);
    } catch (IllegalArgumentException e) {
      return path(Path.of(location));
    }
  }

  /**
   * Convert a location back to a string
   *
   * <p>This is the inverse of {@link #parse(String)}: local paths are returned as-is and URIs keep
   * their scheme (ex. gs://bucket/file) with the path unescaped. A query or fragment (which {@link
   * #parse(String)} never produces, but a URI built in an olive might have) is kept escaped.
   *
   * @param location a <code>PATH{path} | URI{uri}</code> value
   * @return the string form of the path or URI
   */
  public static String format(AlgebraicValue location) {
    final Object value = location.get(0);
    if (!(value instanceof URI uri) || uri.isOpaque() || uri.getScheme() == null) {
      return value.toString();
    }
    final StringBuilder builder = new StringBuilder();
    builder.append(uri.getScheme()).append("://");
    if (uri.getRawAuthority() != null) {
      builder.append(uri.getRawAuthority());
    }
    if (uri.getPath() != null) {
      builder.append(uri.getPath());
    }
    if (uri.getRawQuery() != null) {
      builder.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      builder.append('#').append(uri.getRawFragment());
    }
    return builder.toString();
  }

  /**
   * Escape a raw path so it can be used as the path of a URI
   *
   * <p>Every character other than letters, digits, <code>-._~!$&amp;'()*+,;=@/</code> is
   * percent-encoded as UTF-8, including <code>%</code> (so existing escapes are treated as literal
   * text), <code>?</code>, <code>#</code>, and <code>:</code> (so a relative path like <code>a:b
   * </code> is not mistaken for a scheme).
   *
   * @param path the raw path
   * @return the escaped path
   */
  public static String escapePath(String path) {
    final StringBuilder builder = new StringBuilder(path.length());
    for (final byte b : path.getBytes(StandardCharsets.UTF_8)) {
      final int c = b & 0xFF;
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || "-._~!$&'()*+,;=@/".indexOf(c) >= 0) {
        builder.append((char) c);
      } else {
        builder.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
      }
    }
    return builder.toString();
  }

  /**
   * Create a local location
   *
   * @param path the local path
   * @return the location as a <code>PATH{path}</code> value
   */
  public static AlgebraicValue path(Path path) {
    return new AlgebraicValue("PATH", path);
  }

  /**
   * Create a remote location
   *
   * @param uri the remote URI
   * @return the location as a <code>URI{uri}</code> value
   */
  public static AlgebraicValue uri(URI uri) {
    return new AlgebraicValue("URI", uri);
  }

  private Location() {}
}
