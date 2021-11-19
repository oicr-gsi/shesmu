package ca.on.oicr.gsi.shesmu.server;

public final class CommandStatistics {
  private final long collateralDamage;
  private final long executed;
  private final long ignored;
  private final long purged;

  public CommandStatistics(long executed, long ignored, long purged, long collateralDamage) {
    this.executed = executed;
    this.ignored = ignored;
    this.purged = purged;
    this.collateralDamage = collateralDamage;
  }

  public long getCollateralDamage() {
    return collateralDamage;
  }

  public long getExecuted() {
    return executed;
  }

  public long getIgnored() {
    return ignored;
  }

  public long getPurged() {
    return purged;
  }
}
