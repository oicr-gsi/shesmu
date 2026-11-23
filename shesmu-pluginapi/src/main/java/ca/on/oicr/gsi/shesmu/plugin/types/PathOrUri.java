package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file that may be on the local file system or at a remote URI
 *
 * <p>The Shesmu type is <code>PATH{path} | URI{scheme = string, host = string, name = string}
 * </code>. Use this rather than a <code>path</code> for files that might be remote (ex.
 * gs://bucket/file), since {@link Path#of(String, String...)} collapses the "//" after the scheme.
 *
 * <p>In an olive, the fields of <code>URI</code> may be written in any order, but in Java, an
 * {@link AlgebraicValue} for it must have its fields in alphabetical order (host, name, scheme), as
 * in {@link #DESCRIPTOR}.
 */
public final class PathOrUri {
  /** The descriptor for {@link #TYPE} */
  public static final String DESCRIPTOR = "u2PATH$t1pURI$o3host$sname$sscheme$s";

  /**
   * The Shesmu type: <code>PATH{path} | URI{scheme = string, host = string, name = string}</code>
   */
  public static final Imyhat TYPE = Imyhat.parse(DESCRIPTOR);

  // The host stops at the first /, ?, or #; DOTALL, since an object name may contain any
  // character, including a newline
  private static final Pattern SCHEME =
      Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)$", Pattern.DOTALL);

  /**
   * Convert a string to a path or URI
   *
   * <p>A string with a scheme (ex. gs://bucket/a/b.bam) becomes <code>URI</code>, with the scheme
   * (gs), the host (bucket), and everything after the host, without the / that separates it from
   * the host, as the name (a/b.bam), taken literally, so {@link #format(AlgebraicValue)} returns
   * the original string, except that a / is added after the host if there is none (ex. gs://bucket
   * becomes gs://bucket/ and gs://bucket?x becomes gs://bucket/?x). The exception is a local file
   * URI (file:// with no host or localhost), which becomes <code>PATH</code>, with its path decoded
   * if it is a valid URI with no query or fragment (ex. file:///a%20b becomes /a b). Anything else
   * becomes <code>PATH</code>, exactly as {@link Path#of(String, String...)} parses it.
   *
   * @param input the string to convert
   * @return the value as <code>PATH{path} | URI{scheme = string, host = string, name = string}
   *     </code>
   */
  public static AlgebraicValue parse(String input) {
    final Matcher matcher = SCHEME.matcher(input);
    if (matcher.matches()) {
      final String scheme = matcher.group(1);
      final String host = matcher.group(2);
      final String rest = matcher.group(3);
      if (scheme.equalsIgnoreCase("file")
          && (host.isEmpty() || host.equalsIgnoreCase("localhost"))) {
        return new AlgebraicValue("PATH", localFilePath(input, rest));
      }
      // Fields are in alphabetical order: host, name, scheme
      return new AlgebraicValue(
          "URI", host, rest.startsWith("/") ? rest.substring(1) : rest, scheme);
    }
    return new AlgebraicValue("PATH", Path.of(input));
  }

  /**
   * Get the path of a local file URI
   *
   * @param input the whole URI
   * @param name everything after the host
   * @return the decoded path if the input is a valid URI with no query or fragment and the decoded
   *     path is a valid path; otherwise, the name, taken literally
   */
  private static Path localFilePath(String input, String name) {
    try {
      final URI uri = new URI(input);
      if (uri.getRawQuery() == null
          && uri.getRawFragment() == null
          && uri.getPath() != null
          && !uri.getPath().isEmpty()) {
        return Path.of(uri.getPath());
      }
    } catch (URISyntaxException | InvalidPathException e) {
      // Not escaped (ex. it contains a space), or the decoded path is not a valid path (ex. it
      // contains %00), so take it literally
    }
    return Path.of(name.isEmpty() ? "/" : name);
  }

  /**
   * Convert a path or URI back to a string
   *
   * <p>A URI becomes <i>scheme</i>://<i>host</i>/<i>name</i>, with the fields taken literally.
   *
   * <p>This is the inverse of {@link #parse(String)}, except that paths are normalized as {@link
   * Path#of(String, String...)} does (ex. a trailing / is removed), local file URIs become paths,
   * and a / is added after the host of a URI if there is none.
   *
   * @param value a <code>PATH{path} | URI{scheme = string, host = string, name = string}</code>
   *     value
   * @return the string form
   */
  public static String format(AlgebraicValue value) {
    if (value.name().equals("URI")) {
      return value.get(2) + "://" + value.get(0) + "/" + value.get(1);
    }
    return value.get(0).toString();
  }

  /**
   * Get the last element of a path or URI
   *
   * <p>For a path, this is {@link Path#getFileName()} (ex. b.bam for /data/a/b.bam), or an empty
   * string if the path has no elements (ex. /). For a URI, this is everything in the name after the
   * final / (ex. b.bam for gs://bucket/a/b.bam)
   *
   * @param value a <code>PATH{path} | URI{scheme = string, host = string, name = string}</code>
   *     value
   * @return the last element
   */
  public static String fileName(AlgebraicValue value) {
    if (value.name().equals("URI")) {
      final String name = (String) value.get(1);
      return name.substring(name.lastIndexOf('/') + 1);
    }
    final Path fileName = ((Path) value.get(0)).getFileName();
    return fileName == null ? "" : fileName.toString();
  }

  private PathOrUri() {}
}
