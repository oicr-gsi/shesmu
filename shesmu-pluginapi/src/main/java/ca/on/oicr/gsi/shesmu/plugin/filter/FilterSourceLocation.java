package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public class FilterSourceLocation extends FilterJson {
  private LocationJson[] locations;

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.fromSourceLocation(Stream.of(locations)), filterBuilder);
  }

  public LocationJson[] getLocations() {
    return locations;
  }

  public void setLocations(LocationJson[] locations) {
    this.locations = locations;
  }
}
