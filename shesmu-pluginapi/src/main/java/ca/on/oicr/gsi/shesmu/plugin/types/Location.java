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
 * </code> for values that might be remote (ex. gs://bucket/file), since {@link Path#of(String,
 * String...)} collapses the "//" after the scheme.
 */
public final class Location {
  /** The descriptor for {@link #TYPE} */
  public static final String DESCRIPTOR = "u2PATH$t1pURI$t1r";

  /** The Shesmu type: <code>PATH{path} | URI{uri}</code> */
  public static final Imyhat TYPE =
      Imyhat.algebraicTuple("PATH", Imyhat.PATH).unify(Imyhat.algebraicTuple("URI", Imyhat.URI));

  private static final Pattern SCHEME =
      Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)$");

  /**
   * Create a location from a string
   *
   * @param location the location; strings with a scheme (ex. gs://bucket/file) become <code>URI
   *     </code>, except file:// which becomes <code>PATH</code>; everything else is treated as a
   *     local path
   * @return the location as a <code>PATH{path} | URI{uri}</code> value
   */
  public static AlgebraicValue parse(String location) {
    final Matcher matcher = SCHEME.matcher(location);
    if (!matcher.matches()) {
      return path(Path.of(location));
    }
    URI uri;
    try {
      uri = new URI(location);
    } catch (URISyntaxException e) {
      // Not properly escaped (ex. contains spaces); let the multi-argument constructor quote the
      // illegal characters
      try {
        uri = new URI(matcher.group(1), matcher.group(2), matcher.group(3), null, null);
      } catch (URISyntaxException inner) {
        throw new IllegalArgumentException("Invalid URI: " + location, inner);
      }
    }
    if (uri.getScheme().equalsIgnoreCase("file")) {
      return path(Path.of(uri.getPath()));
    }
    return uri(uri);
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
