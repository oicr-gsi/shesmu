package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** @author mlaszloffy */
public class Barcode {

  private final String barcodeOne;
  private final String barcodeTwo;

  public Barcode() {
    this.barcodeOne = "";
    this.barcodeTwo = null;
  }

  public Barcode(String barcode) {
    Objects.requireNonNull(barcode);
    this.barcodeOne = barcode.toUpperCase();
    this.barcodeTwo = null;
  }

  public Barcode(String barcodeOne, String barcodeTwo) {
    Objects.requireNonNull(barcodeOne);
    Objects.requireNonNull(barcodeTwo);
    this.barcodeOne = barcodeOne.toUpperCase();
    this.barcodeTwo = barcodeTwo.toUpperCase();
  }

  public String getBarcodeOne() {
    return barcodeOne;
  }

  public String getBarcodeTwo() {
    return barcodeTwo;
  }

  public String getLengthString() {
    if (barcodeOne.isEmpty()) {
      return "NoIndex";
    } else if (barcodeTwo == null) {
      return Integer.toString(barcodeOne.length());
    } else {
      return Integer.toString(barcodeOne.length()) + "x" + Integer.toString(barcodeTwo.length());
    }
  }

  @Override
  public String toString() {
    if (barcodeTwo == null) {
      return barcodeOne;
    } else {
      return barcodeOne + "-" + barcodeTwo;
    }
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 79 * hash + Objects.hashCode(this.barcodeOne);
    hash = 79 * hash + Objects.hashCode(this.barcodeTwo);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Barcode other = (Barcode) obj;
    if (!Objects.equals(this.barcodeOne, other.barcodeOne)) {
      return false;
    }
    if (!Objects.equals(this.barcodeTwo, other.barcodeTwo)) {
      return false;
    }
    return true;
  }

  private static final Pattern SINGLE_BARCODE_PATTERN =
      Pattern.compile("([ATCG]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern DUAL_BARCODE_PATTERN =
      Pattern.compile("([ATCG]+)-([ATCG]+)", Pattern.CASE_INSENSITIVE);

  public static Barcode fromString(String barcodeString) {
    Matcher m;

    if (barcodeString == null || barcodeString.isEmpty() || "NoIndex".equals(barcodeString)) {
      return new Barcode();
    }

    m = SINGLE_BARCODE_PATTERN.matcher(barcodeString);
    if (m.matches()) {
      return new Barcode(m.group(1));
    }

    m = DUAL_BARCODE_PATTERN.matcher(barcodeString);
    if (m.matches()) {
      return new Barcode(m.group(1), m.group(2));
    }

    return null;
  }
}
