package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
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
  private static final Pattern PORT = Pattern.compile(":[0-9]*$");
  // A local file URI with no authority (ex. file:/data/a.bam, as from File.toURI())
  private static final Pattern FILE_NO_AUTHORITY =
      Pattern.compile("^[Ff][Ii][Ll][Ee]:(/(?!/).*)$", Pattern.DOTALL);
  // DOTALL, since a name may contain a newline (ex. gs://bucket/a\nb)
  private static final Pattern SCHEME =
      Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)$", Pattern.DOTALL);

  /**
   * Create a location from a string
   *
   * @param location the location; strings with a scheme (ex. gs://bucket/file) become <code>URI
   *     </code>; everything else is treated as a local path. Everything after the authority is
   *     taken literally (it is escaped, not decoded), so {@link #format(AlgebraicValue)} returns
   *     the original string. The exception is file:// on the local machine (no host or localhost),
   *     or file:/ with no authority (ex. file:/data/a.bam, as {@link java.io.File#toURI()}
   *     produces), which becomes a <code>PATH</code>: if it is a valid URI without a query or
   *     fragment, its path is decoded (ex. file:///a%20b becomes /a b), as file URIs are normally
   *     escaped; otherwise, everything after the authority is taken literally. A file:// URI with
   *     any other host is kept as a <code>URI</code>, since it is not a local file; it too is
   *     treated as an escaped URI if it is valid (so file://host/a%20b has the path /a b and {@link
   *     #format(AlgebraicValue)} returns file://host/a b, not the original string), and otherwise
   *     everything after the authority is taken literally.
   * @return the location as a <code>PATH{path} | URI{uri}</code> value
   * @throws IllegalArgumentException if the string has a scheme but cannot be made into a URI (ex.
   *     <code>gs://</code>)
   */
  public static AlgebraicValue parse(String location) {
    final Matcher matcher = SCHEME.matcher(location);
    if (!matcher.matches()) {
      final Matcher file = FILE_NO_AUTHORITY.matcher(location);
      return path(Path.of(file.matches() ? filePath(location, file.group(1)) : location));
    }
    // Everything after the authority is a raw name (as in gs://bucket/name, where the object name
    // may contain any character), not a URI path that is already escaped. Escape all of it so that
    // characters like %, ?, and # are kept as part of the name and format() can restore it exactly.
    if (matcher.group(1).equalsIgnoreCase("file")) {
      final String host = matcher.group(2);
      if (host.isEmpty() || host.equalsIgnoreCase("localhost")) {
        return path(Path.of(filePath(location, matcher.group(3))));
      }
      try {
        return uri(new URI(location));
      } catch (URISyntaxException e) {
        // Fall through and escape it like any other remote location
      }
    }
    try {
      return uri(
          new URI(matcher.group(1) + "://" + matcher.group(2) + escapePath(matcher.group(3))));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URI: " + location, e);
    }
  }

  /**
   * Get the local path of a local file URI (file://, file://localhost, or file:/)
   *
   * @param location the whole file URI
   * @param rest everything after the authority (or after "file:" if there is none)
   * @return the decoded path if the location is a valid URI with no query or fragment; otherwise,
   *     everything after the authority, taken literally
   */
  private static String filePath(String location, String rest) {
    try {
      final URI uri = new URI(location);
      if (uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getPath() != null) {
        return uri.getPath();
      }
    } catch (URISyntaxException e) {
      // Not a valid URI (ex. it contains a space), so it was not escaped; take it literally
    }
    return rest;
  }

  /**
   * Create a URI from a string, without failing on malformed input
   *
   * <p>This is meant for reading URIs from external data (ex. JSON), where a single malformed value
   * should not stop every record from loading. If the string is not a valid URI but has a scheme
   * and authority (ex. gs://bucket/my file), everything after the authority is escaped, as {@link
   * #parse(String)} does, so it keeps its scheme and host. Anything else is escaped entirely as a
   * relative URI (as {@link #escapePath(String)} does), so the original text can still be recovered
   * by decoding its path.
   *
   * @param input the URI as a string
   * @return the URI; malformed input is escaped rather than rejected
   */
  public static URI parseUriLeniently(String input) {
    try {
      return new URI(input);
    } catch (URISyntaxException e) {
      // Not valid as-is; try escaping it below
    }
    final Matcher matcher = SCHEME.matcher(input);
    if (matcher.matches()) {
      try {
        return new URI(matcher.group(1) + "://" + matcher.group(2) + escapePath(matcher.group(3)));
      } catch (URISyntaxException e) {
        // The authority itself is invalid (ex. it contains a space); escape everything
      }
    }
    // The escaped form contains only characters valid in a relative path, and ":" is escaped, so
    // it cannot be mistaken for a scheme. A leading "//" would be read as an authority, so escape
    // the second slash; the decoded path is unchanged.
    final String escaped = escapePath(input);
    return URI.create(escaped.startsWith("//") ? "/%2F" + escaped.substring(2) : escaped);
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
   * Create a location from a string, falling back to a local path if it is not a valid URI, and
   * reusing an already-created path
   *
   * <p>This is for plugins that provide both a path and a location for the same file: when the
   * location is the same local path, it shares the path object rather than storing a second copy.
   *
   * @param location the location
   * @param path the result of {@link Path#of(String, String...)} on the location
   * @return the location as a <code>PATH{path} | URI{uri}</code> value
   * @see #parseOrPath(String)
   */
  public static AlgebraicValue parseOrPath(String location, Path path) {
    // Without a scheme (or file:/), the location is a local path, and that is exactly Path.of's
    // result, so reuse it rather than parsing again
    return SCHEME.matcher(location).matches() || FILE_NO_AUTHORITY.matcher(location).matches()
        ? parseOrPath(location)
        : path(path);
  }

  /**
   * Convert a location back to a string
   *
   * <p>This is the inverse of {@link #parse(String)}, except for file:// URIs, which {@link
   * #parse(String)} decodes: local paths are returned as-is and URIs keep their scheme (ex.
   * gs://bucket/file) with the path unescaped. A query or fragment (which {@link #parse(String)}
   * only produces for a valid file:// URI on another host, but a URI built in an olive might have)
   * is kept escaped.
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
    builder.append(uri.getScheme()).append(':');
    // Only write "//" if the URI had it (ex. gs://bucket/x or gs:///x, but not gs:/x)
    if (uri.getRawSchemeSpecificPart().startsWith("//")) {
      builder.append("//");
      if (uri.getRawAuthority() != null) {
        builder.append(uri.getRawAuthority());
      }
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

  /**
   * Get the host of a URI
   *
   * <p>This is the authority without any user information or port. Unlike {@link URI#getHost()}, it
   * also works for authorities that are not valid host names (ex. the bucket in gs://my_bucket,
   * since _ is not allowed in a host name).
   *
   * @param uri the URI
   * @return the decoded host, or empty if the URI has no authority
   */
  public static Optional<String> host(URI uri) {
    // Split the raw authority, so an escaped @ or : (ex. gs://a%40b) is part of the host rather
    // than a separator, then decode
    final String authority = uri.getRawAuthority();
    if (authority == null) {
      return Optional.empty();
    }
    final String host =
        PORT.matcher(authority.substring(authority.lastIndexOf('@') + 1)).replaceFirst("");
    if (host.isEmpty()) {
      return Optional.empty();
    }
    try {
      // Decode it as URI.getAuthority() does (including replacing invalid UTF-8); it came from a
      // valid authority, so it is still valid on its own
      return Optional.of(URI.create("x://" + host).getAuthority());
    } catch (IllegalArgumentException e) {
      return Optional.of(host);
    }
  }

  private Location() {}
}
