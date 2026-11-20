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

  @Test
  public void testFileUri() {
    // Valid file URIs are escaped, so they are decoded
    assertPath("/data/a b.bam", Location.parse("file:///data/a%20b.bam"));
    assertPath("/data/a/b.bam", Location.parse("file://localhost/data/a/b.bam"));
    // Not valid URIs, or with a query/fragment, are taken literally
    assertPath("/data/a b.bam", Location.parse("file:///data/a b.bam"));
    assertPath("/data/a?b.bam", Location.parse("file:///data/a?b.bam"));
    // With no authority, as from File.toURI()
    assertPath("/data/a b.bam", Location.parse("file:/data/a%20b.bam"));
    assertPath("/data/a.bam", Location.parse("FILE:/data/a.bam"));
    assertPath("/", Location.parse("file:/"));
    assertPath("file:relative", Location.parse("file:relative"));
    final Path path = Path.of("file:/data/a.bam");
    assertPath("/data/a.bam", Location.parseOrPath("file:/data/a.bam", path));
    // Files on another host are not local
    assertUri("file://otherhost/share/x", Location.parse("file://otherhost/share/x"));
    assertUri("file://otherhost/share/a%20b", Location.parse("file://otherhost/share/a b"));
  }

  @Test
  public void testNewline() {
    assertUri("gs://bucket/a%0Ab", Location.parse("gs://bucket/a\nb"));
    Assertions.assertEquals(
        "gs://bucket/a\nb", Location.format(Location.parse("gs://bucket/a\nb")));
  }

  @Test
  public void testFormatUri() {
    Assertions.assertEquals(
        "gs://bucket/a b#1.bam",
        Location.format(Location.uri(URI.create("gs://bucket/a%20b%231.bam"))));
    Assertions.assertEquals("a%20b", Location.format(Location.uri(URI.create("a%20b"))));
    // "//" is only written if the URI had it
    Assertions.assertEquals(
        "gs:/bucket/x", Location.format(Location.uri(URI.create("gs:/bucket/x"))));
    Assertions.assertEquals("gs:///x", Location.format(Location.uri(URI.create("gs:///x"))));
    Assertions.assertEquals("gs:///x", Location.format(Location.parse("gs:///x")));
    Assertions.assertEquals(
        "mailto:a%20b", Location.format(Location.uri(URI.create("mailto:a%20b"))));
  }

  @Test
  public void testParseOrPathShared() {
    final Path path = Path.of("/data/a.bam");
    Assertions.assertSame(path, Location.parseOrPath("/data/a.bam", path).get(0));
    assertUri(
        "gs://bucket/a.bam",
        Location.parseOrPath("gs://bucket/a.bam", Path.of("gs://bucket/a.bam")));
    assertPath(
        "/data/a b.bam",
        Location.parseOrPath("file:///data/a%20b.bam", Path.of("file:///data/a%20b.bam")));
  }

  @Test
  public void testParseUriLeniently() {
    Assertions.assertEquals(
        URI.create("gs://bucket/x"), Location.parseUriLeniently("gs://bucket/x"));
    // A malformed name keeps its scheme and host
    final URI name = Location.parseUriLeniently("gs://bucket/my file.bam");
    Assertions.assertEquals("gs", name.getScheme());
    Assertions.assertEquals("bucket", name.getHost());
    Assertions.assertEquals("/my file.bam", name.getPath());
    // A malformed host cannot be kept, so everything is escaped
    final URI bad = Location.parseUriLeniently("gs://bu cket/x");
    Assertions.assertNull(bad.getScheme());
    Assertions.assertEquals("gs://bu cket/x", bad.getPath());
    // A leading "//" is not mistaken for an authority
    Assertions.assertEquals("//", Location.parseUriLeniently("//").getPath());
    Assertions.assertEquals("//a b", Location.parseUriLeniently("//a b").getPath());
  }

  @Test
  public void testHost() {
    Assertions.assertEquals(
        "example.com", Location.host(URI.create("https://user:pw@example.com:8443/x")).get());
    Assertions.assertEquals("my_bucket", Location.host(URI.create("gs://my_bucket/x")).get());
    Assertions.assertEquals("[::1]", Location.host(URI.create("http://[::1]:80/x")).get());
    Assertions.assertTrue(Location.host(URI.create("mailto:a")).isEmpty());
    // Escaped separators are part of the host
    Assertions.assertEquals("a@b", Location.host(URI.create("gs://a%40b/x")).get());
    Assertions.assertEquals("bucket:12", Location.host(URI.create("gs://bucket%3A12/x")).get());
    Assertions.assertEquals("a b", Location.host(URI.create("gs://u@a%20b:1/x")).get());
    Assertions.assertEquals("a:b", Location.host(URI.create("gs://a:b/x")).get());
  }
}
