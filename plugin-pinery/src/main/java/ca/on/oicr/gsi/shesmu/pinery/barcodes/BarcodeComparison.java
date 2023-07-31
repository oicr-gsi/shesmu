package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.text.similarity.HammingDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * @author mlaszloffy
 */
public class BarcodeComparison {

  private static final LevenshteinDistance LEVENSHTEIN_DISTANCE =
      LevenshteinDistance.getDefaultInstance();
  private static final HammingDistance HAMMING_DISTANCE = new HammingDistance();

  public static int calculateEditDistance(Barcode left, Barcode right) {
    int editDistance = 0;
    editDistance +=
        LEVENSHTEIN_DISTANCE.apply(
            Objects.toString(left.getBarcodeOne(), ""),
            Objects.toString(right.getBarcodeOne(), ""));
    editDistance +=
        LEVENSHTEIN_DISTANCE.apply(
            Objects.toString(left.getBarcodeTwo(), ""),
            Objects.toString(right.getBarcodeTwo(), ""));
    return editDistance;
  }

  public static int calculateTruncatedHammingDistance(Barcode target, Barcode other) {
    int editDistance = 0;

    String targetBarcodeOne = target.getBarcodeOne();
    String otherBarcodeOne = other.getBarcodeOne();
    if (otherBarcodeOne.length() > targetBarcodeOne.length()) {
      otherBarcodeOne = otherBarcodeOne.substring(0, targetBarcodeOne.length());
    } else {
      editDistance += targetBarcodeOne.length() - otherBarcodeOne.length();
      targetBarcodeOne = targetBarcodeOne.substring(0, otherBarcodeOne.length());
    }
    editDistance += HAMMING_DISTANCE.apply(targetBarcodeOne, otherBarcodeOne);

    String targetBarcodeTwo = Objects.toString(target.getBarcodeTwo(), "");
    String otherBarcodeTwo = Objects.toString(other.getBarcodeTwo(), "");
    if (otherBarcodeTwo.length() > targetBarcodeTwo.length()) {
      otherBarcodeTwo = otherBarcodeTwo.substring(0, targetBarcodeTwo.length());
    } else {
      editDistance += targetBarcodeTwo.length() - otherBarcodeTwo.length();
      targetBarcodeTwo = targetBarcodeTwo.substring(0, otherBarcodeTwo.length());
    }
    editDistance += HAMMING_DISTANCE.apply(targetBarcodeTwo, otherBarcodeTwo);

    return editDistance;
  }

  public static List<BarcodeCollision> getTruncatedHammingDistanceCollisions(
      List<Barcode> barcodes, int minAllowedEditDistance) {
    List<BarcodeCollision> collisions = new ArrayList<>();
    for (Barcode target : barcodes) {
      for (Barcode other : barcodes) {
        if (target == other) {
          continue;
        }
        if (calculateTruncatedHammingDistance(target, other) < minAllowedEditDistance) {
          collisions.add(new BarcodeCollision(target, other));
        }
      }
    }
    return collisions;
  }

  public static List<BarcodeCollision> getEditDistanceCollisions(
      List<Barcode> barcodes, int minAllowedEditDistance) {
    List<BarcodeCollision> collisions = new ArrayList<>();
    for (int i = 0; i < barcodes.size(); i++) {
      Barcode target = barcodes.get(i);
      for (int j = i + 1; j < barcodes.size(); j++) {
        Barcode other = barcodes.get(j);
        if (calculateEditDistance(target, other) < minAllowedEditDistance) {
          collisions.add(new BarcodeCollision(target, other));
        }
      }
    }
    return collisions;
  }
}
