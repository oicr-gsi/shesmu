package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** @author mlaszloffy */
public class BasesMaskTest {

  @Test
  public void testPairedEndSingleBarcodeParsing() {
    Assertions.assertEquals(BasesMask.fromString("y1n1,i1n1,y1n1").toString(), "y1n1,i1n1,y1n1");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i1n1,y*n0").toString(), "y*,i1n1,y*");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i6n0,y*n0").toString(), "y*,i6,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n*,y*").toString(), "y*,i6n*,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n2,y*").toString(), "y*,i6n2,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6,y*").toString(), "y*,i6,y*");
  }

  @Test
  public void testPairedEndDualBarcodeParsing() {
    Assertions.assertEquals(
        BasesMask.fromString("y1n1,i1n1,i1n1,y1n1").toString(), "y1n1,i1n1,i1n1,y1n1");
    Assertions.assertEquals(
        BasesMask.fromString("y*n0,i1n1,i1n1,y*n0").toString(), "y*,i1n1,i1n1,y*");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i6n0,i6n0,y*n0").toString(), "y*,i6,i6,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n*,i6n*,y*").toString(), "y*,i6n*,i6n*,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n2,i6n2,y*").toString(), "y*,i6n2,i6n2,y*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6,i6,y*").toString(), "y*,i6,i6,y*");
  }

  @Test
  public void testSingleEndSingleBarcodeParsing() {
    Assertions.assertEquals(BasesMask.fromString("y1n1,i1n1").toString(), "y1n1,i1n1");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i1n1").toString(), "y*,i1n1");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i6n0").toString(), "y*,i6");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n*").toString(), "y*,i6n*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n2").toString(), "y*,i6n2");
    Assertions.assertEquals(BasesMask.fromString("y*,i6").toString(), "y*,i6");
  }

  @Test
  public void testSingleEndDualBarcodeParsing() {
    Assertions.assertEquals(BasesMask.fromString("y1n1,i1n1,i1n1").toString(), "y1n1,i1n1,i1n1");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i1n1,i1n1").toString(), "y*,i1n1,i1n1");
    Assertions.assertEquals(BasesMask.fromString("y*n0,i6n0,i6n0").toString(), "y*,i6,i6");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n*,i6n*").toString(), "y*,i6n*,i6n*");
    Assertions.assertEquals(BasesMask.fromString("y*,i6n2,i6n2").toString(), "y*,i6n2,i6n2");
  }
}
