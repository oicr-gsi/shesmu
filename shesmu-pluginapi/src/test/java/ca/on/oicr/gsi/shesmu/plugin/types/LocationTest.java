package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LocationTest {
  private static void assertPath(String expected, AlgebraicValue value) {
    Assertions.assertEquals("PATH", value.name());
    Assertions.assertEquals(Path.of(expected), value.get(0));
  }

  private static void assertUri(String expected, AlgebraicValue value) {
    Assertions.assertEquals("URI", value.name());
    Assertions.assertEquals(expected, ((URI) value.get(0)).toString());
  }

  @Test
  public void testDescriptor() {
    Assertions.assertEquals(Location.DESCRIPTOR, Location.TYPE.descriptor());
    Assertions.assertTrue(Location.TYPE.isSame(Imyhat.parse(Location.DESCRIPTOR)));
  }

  @Test
  public void testLocal() {
    assertPath("/data/a/b.bam", Location.parse("/data/a/b.bam"));
    assertPath("relative/x", Location.parse("relative/x"));
    assertPath("/data/gs://x", Location.parse("/data/gs://x"));
    assertPath("/data/a/b.bam", Location.parse("file:///data/a/b.bam"));
  }

  @Test
  public void testRemote() {
    final AlgebraicValue value = Location.parse("gs://bucket/a/b.bam");
    assertUri("gs://bucket/a/b.bam", value);
    final URI uri = (URI) value.get(0);
    Assertions.assertEquals("gs", uri.getScheme());
    Assertions.assertEquals("bucket", uri.getAuthority());
    Assertions.assertEquals("/a/b.bam", uri.getPath());
    assertUri("s3://bucket/x", Location.parse("s3://bucket/x"));
  }

  @Test
  public void testFormat() {
    Assertions.assertEquals("/data/a/b.bam", Location.format(Location.parse("/data/a/b.bam")));
    Assertions.assertEquals(
        "gs://bucket/a/b.bam", Location.format(Location.parse("gs://bucket/a/b.bam")));
  }

  @Test
  public void testUnescaped() {
    final AlgebraicValue value = Location.parse("gs://bucket/a b.bam");
    assertUri("gs://bucket/a%20b.bam", value);
    Assertions.assertEquals("/a b.bam", ((URI) value.get(0)).getPath());
  }
}
