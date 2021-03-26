package ca.on.oicr.gsi.shesmu.intervals;

import java.util.Objects;

final class IntervalKey {
  private final String genome;
  private final String library;
  private final String panel;

  IntervalKey(String panel, String library, String genome) {
    this.panel = panel;
    this.library = library;
    this.genome = genome;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IntervalKey that = (IntervalKey) o;
    return panel.equals(that.panel) && library.equals(that.library) && genome.equals(that.genome);
  }

  @Override
  public int hashCode() {
    return Objects.hash(panel, library, genome);
  }

  @Override
  public String toString() {
    return "{"
        + "genome='"
        + genome
        + '\''
        + ", library='"
        + library
        + '\''
        + ", panel='"
        + panel
        + '\''
        + '}';
  }
}
