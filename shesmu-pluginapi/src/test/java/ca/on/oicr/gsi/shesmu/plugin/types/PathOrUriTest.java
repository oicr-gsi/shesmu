package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PathOrUriTest {
  private static void assertPath(String input) {
    final AlgebraicValue value = PathOrUri.parse(input);
    Assertions.assertEquals("PATH", value.name());
    Assertions.assertEquals(Path.of(input), value.get(0));
    Assertions.assertEquals(Path.of(input).toString(), PathOrUri.format(value));
  }

  private static URI assertUri(String input, String escaped) {
    final AlgebraicValue value = PathOrUri.parse(input);
    Assertions.assertEquals("URI", value.name());
    final URI uri = (URI) value.get(0);
    Assertions.assertEquals(escaped, uri.toString());
    Assertions.assertEquals(input, PathOrUri.format(value));
    return uri;
  }

  @Test
  public void testDescriptor() {
    Assertions.assertEquals(PathOrUri.DESCRIPTOR, PathOrUri.TYPE.descriptor());
    Assertions.assertTrue(PathOrUri.TYPE.isSame(Imyhat.parse(PathOrUri.DESCRIPTOR)));
  }

  @Test
  public void testPath() {
    assertPath("/data/a/b.bam");
    assertPath("relative/x");
    assertPath("/data/gs://x");
    // Not a valid URI, so it is a path, as before remote files were supported
    assertPath("gs://");
  }

  @Test
  public void testUri() {
    final URI uri = assertUri("gs://bucket/a/b.bam", "gs://bucket/a/b.bam");
    Assertions.assertEquals("gs", uri.getScheme());
    Assertions.assertEquals("bucket", uri.getAuthority());
    Assertions.assertEquals("/a/b.bam", uri.getPath());
    assertUri("s3://bucket//a/dir/", "s3://bucket//a/dir/");
  }

  @Test
  public void testUriEscaping() {
    assertUri("gs://bucket/a b#1?x%20.bam", "gs://bucket/a%20b%231%3Fx%2520.bam");
  }
}
