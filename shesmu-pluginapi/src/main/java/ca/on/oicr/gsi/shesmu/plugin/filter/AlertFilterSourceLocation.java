package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

/** Alert filter that matches alerts based on the olive that generated them */
public final class AlertFilterSourceLocation extends AlertFilter {
  private SourceOliveLocation[] locations;

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> filterBuilder) {
    return maybeNegate(filterBuilder.fromSourceLocation(Stream.of(locations)), filterBuilder);
  }

  /**
   * Gets the list of olives source definitions that created an alert
   *
   * @return the olives
   */
  public SourceOliveLocation[] getLocations() {
    return locations;
  }

  /**
   * Sets the list of olives source definitions that created an alert
   *
   * @param locations the olives
   */
  public void setLocations(SourceOliveLocation[] locations) {
    this.locations = locations;
  }
}
