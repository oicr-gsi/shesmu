package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

/** An action filter that checks if an action comes from a particular olive definition */
public class ActionFilterSourceLocation extends ActionFilter {
  private SourceOliveLocation[] locations;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.fromSourceLocation(Stream.of(locations)), filterBuilder);
  }

  /**
   * Gets the locations to check
   *
   * @return the locations
   */
  public SourceOliveLocation[] getLocations() {
    return locations;
  }

  /**
   * Sets the locations to check
   *
   * @param locations the locations
   */
  public void setLocations(SourceOliveLocation[] locations) {
    this.locations = locations;
  }

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut.append("Source location action filter for location: ");
    for (SourceOliveLocation location : locations) {
      writeOut.append(location).append(", ");
    }
    return writeOut.toString();
  }
}
