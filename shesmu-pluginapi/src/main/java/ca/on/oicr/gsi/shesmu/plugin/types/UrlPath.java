package ca.on.oicr.gsi.shesmu.plugin.types;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;

/**
 * A path that lives at a URL, such as a file in a Google Cloud Storage bucket (gs://bucket/file)
 *
 * <p>The default file system would collapse the "//" after the scheme, so the scheme and bucket are
 * kept separately and only the remainder is treated as a path. Create these using {@link
 * PathParser#parse(String)}.
 */
public final class UrlPath implements Path {
  private final String bucket;
  private final Path inner;
  private final String scheme;

  UrlPath(String scheme, String bucket, Path inner) {
    this.scheme = scheme;
    this.bucket = bucket;
    this.inner = inner;
  }

  /**
   * Compare two URL paths
   *
   * <p>This backs every set and dictionary of paths, so compare the parts directly rather than
   * building the full URL strings on every comparison
   */
  int compareUrl(UrlPath other) {
    var result = scheme.compareTo(other.scheme);
    if (result == 0) {
      result = bucket.compareTo(other.bucket);
    }
    return result == 0 ? inner.compareTo(other.inner) : result;
  }

  @Override
  public int compareTo(Path other) {
    return PathParser.COMPARATOR.compare(this, other);
  }

  @Override
  public boolean endsWith(Path other) {
    if (other instanceof UrlPath url) {
      return equals(url);
    }
    return !other.isAbsolute() && inner.endsWith(other);
  }

  @Override
  public boolean endsWith(String other) {
    return endsWith(PathParser.parse(other));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UrlPath other)) {
      return false;
    }
    return scheme.equals(other.scheme) && bucket.equals(other.bucket) && inner.equals(other.inner);
  }

  @Override
  public Path getFileName() {
    return inner.getFileName();
  }

  @Override
  public FileSystem getFileSystem() {
    throw new UnsupportedOperationException("URL paths do not belong to a file system: " + this);
  }

  @Override
  public Path getName(int index) {
    return inner.getName(index);
  }

  @Override
  public int getNameCount() {
    return inner.getNameCount();
  }

  @Override
  public Path getParent() {
    final var parent = inner.getParent();
    return parent == null ? null : wrap(parent);
  }

  @Override
  public Path getRoot() {
    return wrap(inner.getRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, bucket, inner);
  }

  @Override
  public boolean isAbsolute() {
    return true;
  }

  @Override
  public Path normalize() {
    return wrap(inner.normalize());
  }

  @Override
  public WatchKey register(
      WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
    throw new UnsupportedOperationException("Cannot watch URL path: " + this);
  }

  @Override
  public Path relativize(Path other) {
    if (other instanceof UrlPath url && scheme.equals(url.scheme) && bucket.equals(url.bucket)) {
      return inner.relativize(url.inner);
    }
    throw new IllegalArgumentException(
        String.format("Cannot relativize %s against %s", other, this));
  }

  @Override
  public Path resolve(Path other) {
    return other.isAbsolute() ? other : wrap(inner.resolve(other));
  }

  @Override
  public Path resolve(String other) {
    return resolve(PathParser.parse(other));
  }

  @Override
  public Path resolveSibling(String other) {
    return resolveSibling(PathParser.parse(other));
  }

  @Override
  public boolean startsWith(Path other) {
    return other instanceof UrlPath url
        && scheme.equals(url.scheme)
        && bucket.equals(url.bucket)
        && inner.startsWith(url.inner);
  }

  @Override
  public boolean startsWith(String other) {
    return startsWith(PathParser.parse(other));
  }

  @Override
  public Path subpath(int beginIndex, int endIndex) {
    return inner.subpath(beginIndex, endIndex);
  }

  @Override
  public Path toAbsolutePath() {
    return this;
  }

  @Override
  public java.io.File toFile() {
    throw new UnsupportedOperationException("URL path is not a local file: " + this);
  }

  @Override
  public Path toRealPath(LinkOption... options) {
    throw new UnsupportedOperationException("Cannot resolve URL path: " + this);
  }

  @Override
  public String toString() {
    return scheme + "://" + bucket + inner;
  }

  @Override
  public URI toUri() {
    try {
      return new URI(scheme, bucket, inner.toString(), null, null);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private UrlPath wrap(Path path) {
    return new UrlPath(scheme, bucket, path);
  }
}
