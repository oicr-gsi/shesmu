package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class BarcodeGrouperTest {
  class Result {
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
            Collections::singletonList,
            (len, barcodes, newMask) ->
                (Result o, String i) ->
                    o.ok &= barcodes.apply(i).iterator().next().equals(i.substring(0, 6)));
    assertTrue(
        grouper
            .group(Arrays.asList("ACGTTT", "TTTAAATA"))
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
            Collections::singletonList,
            (len, barcodes, newMask) ->
                (Result o, String i) ->
                    o.ok &= barcodes.apply(i).iterator().next().equals(i.substring(0, 6)));
    assertEquals(0L, grouper.group(Arrays.asList("CTTTTT", "ATTTTT")).count());
  }
}
