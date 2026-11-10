package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UrlPathTest {
  @Test
  public void testLocalUnchanged() {
    final var path = PathParser.parse("/data/a//b.bam");
    Assertions.assertEquals(Paths.get("/data/a/b.bam"), path);
    Assertions.assertEquals("/data/a/b.bam", path.toString());
    Assertions.assertEquals("relative/x", PathParser.parse("relative/x").toString());
  }

  @Test
  public void testRoundTrip() {
    Assertions.assertEquals(
        "gs://bucket/a/b.bam", PathParser.parse("gs://bucket/a/b.bam").toString());
    Assertions.assertEquals("s3://bucket/x", PathParser.parse("s3://bucket/x").toString());
    Assertions.assertEquals("gs://bucket/", PathParser.parse("gs://bucket").toString());
    Assertions.assertEquals(
        PathParser.parse("gs://bucket/a/b.bam"), PathParser.parse("gs://bucket/a/b.bam"));
    Assertions.assertNotEquals(
        PathParser.parse("gs://bucket/a/b.bam"), PathParser.parse("gs://other/a/b.bam"));
  }

  @Test
  public void testOperations() {
    final var path = PathParser.parse("gs://bucket/a/b.bam");
    Assertions.assertEquals("gs://bucket/a", path.getParent().toString());
    Assertions.assertEquals("b.bam", path.getFileName().toString());
    Assertions.assertEquals("gs://bucket/a/b.bam.bai", path.resolveSibling("b.bam.bai").toString());
    Assertions.assertEquals("gs://bucket/a/c", path.getParent().resolve("c").toString());
    Assertions.assertEquals("gs://other/x", path.resolve("gs://other/x").toString());
    Assertions.assertEquals("/local", path.resolve(Paths.get("/local")).toString());
    Assertions.assertEquals(
        "gs://bucket/b.bam", PathParser.parse("gs://bucket/a/../b.bam").normalize().toString());
    Assertions.assertEquals(
        "a/b.bam", PathParser.parse("gs://bucket/").relativize(path).toString());
    Assertions.assertTrue(path.startsWith("gs://bucket/a"));
    Assertions.assertFalse(path.startsWith("gs://other/a"));
    Assertions.assertFalse(path.startsWith("/a"));
    Assertions.assertTrue(path.endsWith("a/b.bam"));
    Assertions.assertEquals("gs://bucket/a/b.bam", path.toUri().toString());
  }

  @Test
  public void testMixedSorting() {
    final var paths = new TreeSet<Path>(PathParser.COMPARATOR);
    paths.add(PathParser.parse("gs://bucket/b"));
    paths.add(PathParser.parse("/data/a"));
    paths.add(PathParser.parse("gs://bucket/a"));
    Assertions.assertEquals(
        List.of("/data/a", "gs://bucket/a", "gs://bucket/b"),
        paths.stream().map(Path::toString).toList());
    Assertions.assertTrue(
        PathParser.COMPARATOR.compare(PathParser.parse("gs://a/z"), PathParser.parse("gs://b/a"))
            < 0);
    Assertions.assertTrue(
        PathParser.COMPARATOR.compare(PathParser.parse("gs://b/a"), PathParser.parse("s3://a/a"))
            < 0);
  }

  @Test
  public void testNotUrls() {
    Assertions.assertFalse(PathParser.parse("://bucket/x") instanceof UrlPath);
    Assertions.assertFalse(PathParser.parse("/data/gs://x") instanceof UrlPath);
  }
}
