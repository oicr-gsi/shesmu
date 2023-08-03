package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * @author mlaszloffy
 */
public class BarcodeTest {

  public BarcodeTest() {}

  @Test
  public void singleBarcodeTest() {
    assertEquals(Barcode.fromString("AAAA").getBarcodeOne(), "AAAA");
    assertNull(Barcode.fromString("AAAA").getBarcodeTwo());
    assertEquals(Barcode.fromString("ATCG").getBarcodeOne(), "ATCG");
    assertNull(Barcode.fromString("ATCG").getBarcodeTwo());
    assertEquals(Barcode.fromString("aaaa").getBarcodeOne(), "AAAA");
    assertNull(Barcode.fromString("aaaa").getBarcodeTwo());
    assertEquals(Barcode.fromString("aaaA").getBarcodeOne(), "AAAA");
    assertNull(Barcode.fromString("aaaA").getBarcodeTwo());
  }

  @Test
  public void singleBarcodeFailure1Test() {
    assertNull(Barcode.fromString("AAAAB"));
  }

  @Test
  public void singleBarcodeFailure2Test() {
    assertNull(Barcode.fromString("AAAA-"));
  }

  @Test
  public void singleBarcodeFailure3Test() {
    assertNull(Barcode.fromString(" AAAA "));
  }

  @Test
  public void dualBarcodeTest() {
    assertEquals(Barcode.fromString("AAAA-TTTTT").getBarcodeOne(), "AAAA");
    assertEquals(Barcode.fromString("AAAA-TTTTT").getBarcodeTwo(), "TTTTT");
    assertEquals(Barcode.fromString("ATCG-ATCGG").getBarcodeOne(), "ATCG");
    assertEquals(Barcode.fromString("ATCG-ATCGG").getBarcodeTwo(), "ATCGG");
    assertEquals(Barcode.fromString("aaaa-ttttt").getBarcodeOne(), "AAAA");
    assertEquals(Barcode.fromString("aaaa-ttttt").getBarcodeTwo(), "TTTTT");
    assertEquals(Barcode.fromString("aaaA-ttttT").getBarcodeOne(), "AAAA");
    assertEquals(Barcode.fromString("aaaA-ttttT").getBarcodeTwo(), "TTTTT");
  }

  @Test
  public void dualBarcodeFailure1Test() {
    assertNull(Barcode.fromString("AAAA-TTTTB"));
  }

  @Test
  public void dualBarcodeFailure2Test() {
    assertNull(Barcode.fromString("AAAA-TTTT-"));
  }

  @Test()
  public void dualBarcodeFailure3Test() {
    assertNull(Barcode.fromString(" AAAA-TTTT "));
  }
}
