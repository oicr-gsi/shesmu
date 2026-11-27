package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PathOrUriTest {
  private static void assertPath(String input) {
    final AlgebraicValue value = PathOrUri.parse(input);
    Assertions.assertEquals(new AlgebraicValue("PATH", Path.of(input)), value);
    Assertions.assertEquals(Path.of(input).toString(), PathOrUri.format(value));
  }

  private static void assertUri(String input, String scheme, String host, String name) {
    assertUriFormat(input, scheme, host, name, input);
  }

  private static void assertUriFormat(
      String input, String scheme, String host, String name, String formatted) {
    final AlgebraicValue value = PathOrUri.parse(input);
    Assertions.assertEquals(new AlgebraicValue("URI", host, name, scheme), value);
    Assertions.assertEquals(formatted, PathOrUri.format(value));
  }

  @Test
  public void testDescriptor() {
    Assertions.assertEquals(PathOrUri.DESCRIPTOR, PathOrUri.TYPE.descriptor());
  }

  @Test
  public void testPath() {
    assertPath("/data/a/b.bam");
    assertPath("relative/x");
    assertPath("/data/gs://x");
  }

  @Test
  public void testLocalFileUri() {
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/data/x.bam")), PathOrUri.parse("file:///data/x.bam"));
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/data/a b.bam")),
        PathOrUri.parse("file://localhost/data/a%20b.bam"));
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/data/a b.bam")),
        PathOrUri.parse("file:///data/a b.bam"));
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/data/x?1.bam")),
        PathOrUri.parse("file:///data/x?1.bam"));
    // The decoded path would contain a NUL character, so it is taken literally
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/data/a%00b")), PathOrUri.parse("file:///data/a%00b"));
    Assertions.assertEquals(
        new AlgebraicValue("PATH", Path.of("/données/a b")),
        PathOrUri.parse("file:///données/a%20b"));
    // Another host is not local
    assertUri("file://server/data/x.bam", "file", "server", "data/x.bam");
  }

  @Test
  public void testNormalizedPath() {
    Assertions.assertEquals("/data/dir", PathOrUri.format(PathOrUri.parse("/data/dir/")));
  }

  @Test
  public void testFileName() {
    Assertions.assertEquals("b.bam", PathOrUri.fileName(PathOrUri.parse("/data/a/b.bam")));
    Assertions.assertEquals("dir", PathOrUri.fileName(PathOrUri.parse("/data/dir/")));
    Assertions.assertEquals("x", PathOrUri.fileName(PathOrUri.parse("x")));
    Assertions.assertEquals("", PathOrUri.fileName(PathOrUri.parse("/")));
    Assertions.assertEquals("b.bam", PathOrUri.fileName(PathOrUri.parse("gs://bucket/a/b.bam")));
    Assertions.assertEquals(
        "b#1?x.bam", PathOrUri.fileName(PathOrUri.parse("gs://bucket/a/b#1?x.bam")));
    Assertions.assertEquals("", PathOrUri.fileName(PathOrUri.parse("s3://bucket//a/dir/")));
    Assertions.assertEquals("", PathOrUri.fileName(PathOrUri.parse("gs://bucket")));
  }

  @Test
  public void testUri() {
    assertUri("gs://bucket/a/b.bam", "gs", "bucket", "a/b.bam");
    assertUri("s3://bucket//a/dir/", "s3", "bucket", "/a/dir/");
    assertUri("gs://bucket/a b#1?x%20.bam", "gs", "bucket", "a b#1?x%20.bam");
    assertUri("gs://bucket/", "gs", "bucket", "");
    assertUri("gs:///x", "gs", "", "x");
  }

  @Test
  public void testUriWithoutSlash() {
    // A / is added after the host
    assertUriFormat("gs://bucket", "gs", "bucket", "", "gs://bucket/");
    assertUriFormat(
        "gs://bucket?generation=1", "gs", "bucket", "?generation=1", "gs://bucket/?generation=1");
    assertUriFormat("gs://bucket#x", "gs", "bucket", "#x", "gs://bucket/#x");
  }
}
