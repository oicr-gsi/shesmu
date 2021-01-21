package ca.on.oicr.gsi.shesmu.niassa;

public class IusTriple {
  private final String barcode;
  private final int lane;
  private final String run;

  public IusTriple(String run, Long lane, String barcode) {
    this(run, lane.intValue(), barcode);
  }

  public IusTriple(String run, int lane, String barcode) {
    this.run = run;
    this.lane = lane;
    this.barcode = barcode;
  }

  public String barcode() {
    return barcode;
  }

  public int lane() {
    return lane;
  }

  public String run() {
    return run;
  }
}
