package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Converts strings to paths, keeping URL paths (ex. gs://bucket/file) intact
 *
 * <p>Paths with a scheme become {@link UrlPath}, since the default file system would collapse the
 * "//" after the scheme. Local paths (without a scheme) are left as ordinary paths on the default
 * file system, so they are not given a file:// prefix.
 *
 * <p>Plugins must use {@link #parse(String)} instead of {@link Paths#get(String, String...)} or
 * {@link Path#of(String, String...)} whenever they create a Shesmu <code>path</code> value from a
 * string.
 */
public final class PathParser {
  /**
   * Compare paths, allowing URL paths and plain paths to be mixed
   *
   * <p>Plain paths sort before URL paths.
   */
  public static final Comparator<Path> COMPARATOR =
      (left, right) -> {
        if (left instanceof UrlPath leftUrl) {
          return right instanceof UrlPath rightUrl ? leftUrl.compareUrl(rightUrl) : 1;
        } else if (right instanceof UrlPath) {
          return -1;
        } else {
          return left.compareTo(right);
        }
      };

  private static final Pattern URL = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/]*)(.*)$");

  /**
   * Convert a string to a path
   *
   * @param path the path to parse; if it starts with a URL scheme (scheme://), the scheme and
   *     bucket will be preserved
   * @return a URL path if the input has a scheme; otherwise, a path on the default file system
   */
  public static Path parse(String path) {
    // If statement to skip the regular expression unless there is a "://"
    if (path.indexOf("://") >= 1) {
      final var matcher = URL.matcher(path);
      if (matcher.matches()) {
        final var rest = matcher.group(3);
        return new UrlPath(
            matcher.group(1), matcher.group(2), Paths.get(rest.isEmpty() ? "/" : rest));
      }
    }
    return Paths.get(path);
  }

  private PathParser() {}
}
