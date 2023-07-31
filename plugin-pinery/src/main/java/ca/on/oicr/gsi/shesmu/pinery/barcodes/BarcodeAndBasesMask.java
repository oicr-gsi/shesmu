package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mlaszloffy
 */
public class BarcodeAndBasesMask {

  public static Barcode applyBasesMask(Barcode barcode, BasesMask basesMask) {
    if (barcode == null) {
      return null;
    }

    String barcodeOne = barcode.getBarcodeOne();
    String barcodeTwo = barcode.getBarcodeTwo();

    if ((barcodeOne == null || barcodeOne.isEmpty())
        && (barcodeTwo == null || barcodeTwo.isEmpty())) {
      // Empty or NoIndex barcode, no need to apply bases mask
      return barcode;
    }

    if (basesMask.getIndexOneIncludeLength() == null && barcodeOne == null) {
      barcodeOne = null;
    } else if (basesMask.getIndexOneIncludeLength() == null && barcodeOne != null) {
      barcodeOne = null;
    } else if (basesMask.getIndexOneIncludeLength() != null && barcodeOne == null) {
      // should be rejected by strict, but keeping
    } else if (basesMask.getIndexOneIncludeLength() != null && barcodeOne != null) {
      if (barcodeOne.length() < basesMask.getIndexOneIncludeLength()) {
        // do not modify barcode
      } else {
        barcodeOne = barcodeOne.substring(0, basesMask.getIndexOneIncludeLength());
      }
    }

    if (basesMask.getIndexTwoIncludeLength() == null && barcodeTwo == null) {
      barcodeTwo = null;
    } else if (basesMask.getIndexTwoIncludeLength() == null && barcodeTwo != null) {
      barcodeTwo = null;
    } else if (basesMask.getIndexTwoIncludeLength() != null && barcodeTwo == null) {
      // should be rejected by strict, but keeping
    } else if (basesMask.getIndexTwoIncludeLength() != null && barcodeTwo != null) {
      if (barcodeTwo.length() < basesMask.getIndexTwoIncludeLength()) {
        // do not modify barcode
      } else {
        barcodeTwo = barcodeTwo.substring(0, basesMask.getIndexTwoIncludeLength());
      }
    }

    if (barcodeOne != null
        && !barcodeOne.isEmpty()
        && (barcodeTwo == null || barcodeTwo.isEmpty())) {
      return new Barcode(barcodeOne);
    } else if ((barcodeOne == null || barcodeOne.isEmpty())
        && barcodeTwo != null
        && !barcodeTwo.isEmpty()) {
      return new Barcode(barcodeTwo);
    } else if (barcodeOne != null
        && !barcodeOne.isEmpty()
        && barcodeTwo != null
        && !barcodeTwo.isEmpty()) {
      return new Barcode(barcodeOne, barcodeTwo);
    } else {
      return null;
    }
  }

  public static BasesMask calculateBasesMask(Barcode barcode) {

    BasesMask.BasesMaskBuilder basesMaskBuilder = new BasesMask.BasesMaskBuilder();

    basesMaskBuilder.setReadOneIncludeLength(Integer.MAX_VALUE);

    if (barcode.getBarcodeOne().length() > 0) {
      basesMaskBuilder.setIndexOneIncludeLength(barcode.getBarcodeOne().length());
      basesMaskBuilder.setIndexOneIgnoreLength(Integer.MAX_VALUE);
    }

    if (barcode.getBarcodeTwo() != null && barcode.getBarcodeTwo().length() > 0) {
      basesMaskBuilder.setIndexTwoIncludeLength(barcode.getBarcodeTwo().length());
      basesMaskBuilder.setIndexTwoIgnoreLength(Integer.MAX_VALUE);
    }

    basesMaskBuilder.setReadTwoIncludeLength(Integer.MAX_VALUE);

    return basesMaskBuilder.createBasesMask();
  }

  public static BasesMask calculateBasesMask(Barcode barcode, BasesMask runBasesMask) {
    Barcode sequencedBarcode = applyBasesMask(barcode, runBasesMask);
    if (sequencedBarcode == null) {
      return null;
    }
    BasesMask barcodeBasesMask = calculateBasesMask(sequencedBarcode);

    BasesMask.BasesMaskBuilder basesMaskBuilder = new BasesMask.BasesMaskBuilder();

    // read one
    basesMaskBuilder.setReadOneIncludeLength(runBasesMask.getReadOneIncludeLength());
    basesMaskBuilder.setReadOneIgnoreLength(runBasesMask.getReadOneIgnoreLength());

    // index one
    if (runBasesMask.getIndexOneIncludeLength() != null) {
      if (barcodeBasesMask.getIndexOneIncludeLength() == null) {
        if (runBasesMask.getIndexOneIncludeLength() != null) {
          basesMaskBuilder.setIndexOneIgnoreLength(Integer.MAX_VALUE);
        } else {
          basesMaskBuilder.setIndexOneIgnoreLength(null);
        }
      } else {
        basesMaskBuilder.setIndexOneIncludeLength(barcodeBasesMask.getIndexOneIncludeLength());
        basesMaskBuilder.setIndexOneIgnoreLength(barcodeBasesMask.getIndexOneIgnoreLength());
      }
    }
    if (runBasesMask.getIndexOneIgnoreLength() != null) {
      basesMaskBuilder.setIndexOneIgnoreLength(runBasesMask.getIndexOneIgnoreLength());
    }

    // index two
    if (runBasesMask.getIndexTwoIncludeLength() != null) {
      if (barcodeBasesMask.getIndexTwoIncludeLength() == null) {
        if (runBasesMask.getIndexOneIncludeLength() != null) {
          basesMaskBuilder.setIndexTwoIgnoreLength(Integer.MAX_VALUE);
        } else {
          basesMaskBuilder.setIndexTwoIgnoreLength(null);
        }
      } else {
        basesMaskBuilder.setIndexTwoIncludeLength(barcodeBasesMask.getIndexTwoIncludeLength());
        basesMaskBuilder.setIndexTwoIgnoreLength(barcodeBasesMask.getIndexTwoIgnoreLength());
      }
    }
    if (runBasesMask.getIndexTwoIgnoreLength() != null) {
      basesMaskBuilder.setIndexTwoIgnoreLength(runBasesMask.getIndexTwoIgnoreLength());
    }

    // read two
    basesMaskBuilder.setReadTwoIncludeLength(runBasesMask.getReadTwoIncludeLength());
    basesMaskBuilder.setReadTwoIgnoreLength(runBasesMask.getReadTwoIgnoreLength());

    return basesMaskBuilder.createBasesMask();
  }

  public static BasesMask calculateBasesMask(Collection<Barcode> barcodes) {
    Set<BasesMask> bs =
        barcodes.stream().map(BarcodeAndBasesMask::calculateBasesMask).collect(Collectors.toSet());
    if (bs.size() != 1) {
      return null;
    }
    return bs.iterator().next();
  }

  public static BasesMask calculateBasesMask(List<Barcode> barcodes, BasesMask runBasesMask) {
    Set<BasesMask> bs = new HashSet<>();
    for (final Barcode barcode : barcodes) {
      final BasesMask mask = calculateBasesMask(barcode, runBasesMask);
      if (mask == null) {
        return null;
      }
      bs.add(mask);
    }
    if (bs.size() != 1) {
      return null;
    }
    return bs.iterator().next();
  }
}
