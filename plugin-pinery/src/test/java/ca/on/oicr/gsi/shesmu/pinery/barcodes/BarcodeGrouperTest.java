package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import static org.junit.jupiter.api.Assertions.*;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import java.util.List;
import org.junit.jupiter.api.Test;

public class BarcodeGrouperTest {
  static class Result {
    boolean ok = true;

    public boolean check() {
      return ok;
    }
  }

  @Test
  public void testClipping() {
    final String basesMask = "y*,i6";
    final Grouper<String, Result> grouper =
        new BarcodeGrouper<>(
            2,
            i -> "run",
            i -> basesMask,
            List::of,
            (len, barcodes, newMask) ->
                (Result o, String i) ->
                    o.ok &= barcodes.apply(i).iterator().next().equals(i.substring(0, 6)));
    assertTrue(
        grouper
            .group(List.of("ACGTTT", "TTTAAATA"))
            .allMatch(s -> s.build(i -> new Result()).check()));
  }

  @Test
  public void testBad() {
    final String basesMask = "y*,i6";
    final Grouper<String, Result> grouper =
        new BarcodeGrouper<>(
            2,
            i -> "run",
            i -> basesMask,
            List::of,
            (len, barcodes, newMask) ->
                (Result o, String i) ->
                    o.ok &= barcodes.apply(i).iterator().next().equals(i.substring(0, 6)));
    assertEquals(0L, grouper.group(List.of("CTTTTT", "ATTTTT")).count());
  }
}
