package ca.on.oicr.gsi.shesmu.pinery.barcodes;

/** @author mlaszloffy */
public class BarcodeCollision {

  private final Barcode barcode;
  private final Barcode collidesWithBarcode;

  public BarcodeCollision(Barcode barcode, Barcode collidesWithBarcode) {
    this.barcode = barcode;
    this.collidesWithBarcode = collidesWithBarcode;
  }

  @Override
  public String toString() {
    return "BarcodeCollision{"
        + "barcode="
        + barcode
        + ", collidesWithBarcode="
        + collidesWithBarcode
        + '}';
  }
}
